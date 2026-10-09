#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QR-stations F2 (scanner + visit logic) - 08.10.2026

- PATCH: QrStationDao (countVisitsSince)
- PATCH: QrStationRepository (countVisitsToday)
- NEW: QrScanViewModel.kt
- NEW: QrScannerScreen.kt (ZXing ScanContract + ручной ввод)
- PATCH: Routes (QR_SCANNER)
- PATCH: PathfinderNavHost (маршрут + onNavigateToQrScanner)
- PATCH: TeenHomeScreen (плитка "QR-станции")
- PATCH: PathfinderApp (seedDemoStations)
- PATCH: AndroidManifest (CaptureActivity override)

Запуск:
    python fix_qr_f2.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        die(path + ": файл уже существует.")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


def patch(path, repls, skip_if_marker=None):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

    if skip_if_marker and skip_if_marker in text:
        print("[SKIP] " + path + " (уже пропатчен)")
        return

    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)

    for i, (o, n) in enumerate(repls, 1):
        if o not in text:
            die(path + ": шаблон #" + str(i) + ":\n" + o[:400])
        text = text.replace(o, n, 1)
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. QrStationDao — countVisitsSince
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/dao/QrStationDao.kt", [
    ("""    @Query("SELECT COUNT(*) FROM station_visits WHERE userId = :userId AND geoVerified = 1")
    suspend fun countVerifiedVisits(userId: String): Int
}""",
     """    @Query("SELECT COUNT(*) FROM station_visits WHERE userId = :userId AND geoVerified = 1")
    suspend fun countVerifiedVisits(userId: String): Int

    @Query("SELECT COUNT(*) FROM station_visits WHERE userId = :userId AND stationId = :stationId AND scannedAt >= :sinceMs")
    suspend fun countVisitsSince(userId: String, stationId: String, sinceMs: Long): Int
}"""),
], skip_if_marker="countVisitsSince")


# ============================================================
# 2. QrStationRepository — countVisitsToday
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/repository/QrStationRepository.kt", [
    ("""    suspend fun countVerifiedVisits(userId: String): Int =
        dao.countVerifiedVisits(userId)""",
     """    suspend fun countVerifiedVisits(userId: String): Int =
        dao.countVerifiedVisits(userId)

    /**
     * Количество посещений станции пользователем с начала сегодняшнего дня.
     * Используется для блокировки повторного сканирования.
     */
    suspend fun countVisitsToday(userId: String, stationId: String): Int {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return dao.countVisitsSince(userId, stationId, cal.timeInMillis)
    }"""),
], skip_if_marker="countVisitsToday")


# ============================================================
# 3. QrScanViewModel.kt
# ============================================================
VM = '''package com.pathfinder.hub.ui.qr

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.QrStationRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class ScanResult(
    val ok: Boolean,
    val geoVerified: Boolean,
    val stationName: String,
    val stationType: String,
    val distanceMeters: Double?,
    val rewardPoints: Int,
    val message: String,
    val alreadyVisitedToday: Boolean = false,
)

data class QrScanUiState(
    val isProcessing: Boolean = false,
    val lastScan: ScanResult? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class QrScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrRepository: QrStationRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QrScanUiState())
    val state: StateFlow<QrScanUiState> = _state.asStateFlow()

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    fun reset() {
        _state.update { it.copy(lastScan = null, errorMessage = null) }
    }

    /**
     * Обработка отсканированного QR.
     * Поддерживаемые форматы:
     *   PFHUB:STATION:<stationId>
     *   <stationId>  (для ручного ввода / тестов)
     */
    fun onScanned(raw: String) {
        val stationId = parseStationId(raw)
        if (stationId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Неверный формат QR-кода") }
            return
        }
        processStation(stationId)
    }

    private fun parseStationId(raw: String): String? {
        val s = raw.trim()
        if (s.startsWith("PFHUB:STATION:")) return s.removePrefix("PFHUB:STATION:").trim()
        // fallback: считаем, что это уже ID
        return if (s.isNotBlank() && s.length <= 64) s else null
    }

    private fun processStation(stationId: String) {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, errorMessage = null) }

            try {
                // 1) станция
                val station = qrRepository.getStation(stationId)
                if (station == null) {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Станция не найдена: " + stationId
                        )
                    }
                    return@launch
                }

                // 2) блокировка повторного скана сегодня
                val already = qrRepository.countVisitsToday(uid, station.id) > 0
                if (already) {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            lastScan = ScanResult(
                                ok = false,
                                geoVerified = false,
                                stationName = station.name,
                                stationType = station.type,
                                distanceMeters = null,
                                rewardPoints = 0,
                                message = "Вы уже отмечались на этой станции сегодня",
                                alreadyVisitedToday = true,
                            )
                        )
                    }
                    return@launch
                }

                // 3) локация
                val loc = getLocation()
                val distance: Double? = if (loc != null) {
                    QrStationRepository.distanceMeters(
                        station.latitude, station.longitude,
                        loc.latitude, loc.longitude
                    )
                } else null

                val verified = if (loc != null && distance != null) {
                    QrStationRepository.isWithinRadius(
                        station.latitude, station.longitude, station.radiusMeters,
                        loc.latitude, loc.longitude, loc.accuracy
                    )
                } else false

                // 4) сохраняем визит (даже если не verified)
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId ?: station.clubId

                val visit = StationVisitEntity(
                    id = UUID.randomUUID().toString(),
                    stationId = station.id,
                    stationName = station.name,
                    stationType = station.type,
                    userId = uid,
                    clubId = clubId,
                    scannedAt = Date(),
                    userLat = loc?.latitude,
                    userLon = loc?.longitude,
                    accuracyMeters = loc?.accuracy,
                    distanceMeters = distance,
                    geoVerified = verified,
                    rewardPoints = if (verified) station.rewardPoints else 0,
                    pendingSync = true,
                )
                qrRepository.upsertVisit(visit)

                // 5) награда: ачивка (если верифицировано и задана)
                if (verified && !station.rewardAchievementId.isNullOrBlank()) {
                    try {
                        achievementRepository.upsertProgress(
                            AchievementProgressEntity(
                                userId = uid,
                                achievementId = station.rewardAchievementId,
                                status = "earned",
                                earnedAt = Date(),
                                revokedAt = null,
                                revokedBy = null,
                                revokeReason = null,
                                pendingSync = false,
                            )
                        )
                    } catch (e: Exception) {
                        Log.w("QrScan", "achievement grant failed", e)
                    }
                }

                val msg = when {
                    verified -> "Посещение засчитано! +" + station.rewardPoints + " очков"
                    loc == null -> "Посещение сохранено, но гео не подтверждено (нет локации)"
                    else -> "Слишком далеко: " + distance?.toInt() + " м (нужно " +
                            station.radiusMeters + " м). Посещение сохранено как непроверенное."
                }

                _state.update {
                    it.copy(
                        isProcessing = false,
                        lastScan = ScanResult(
                            ok = verified,
                            geoVerified = verified,
                            stationName = station.name,
                            stationType = station.type,
                            distanceMeters = distance,
                            rewardPoints = if (verified) station.rewardPoints else 0,
                            message = msg,
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("QrScan", "process failed", e)
                _state.update {
                    it.copy(isProcessing = false, errorMessage = e.message ?: "Ошибка")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocation(): Location? {
        return try {
            withTimeoutOrNull(10_000L) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }
        } catch (e: Exception) {
            Log.w("QrScan", "getLocation failed", e)
            null
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/QrScanViewModel.kt", VM)


# ============================================================
# 4. QrScannerScreen.kt
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    viewModel: QrScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    var showManualDialog by remember { mutableStateOf(false) }
    var manualId by remember { mutableStateOf("") }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            viewModel.onScanned(contents)
        }
    }

    fun launchScanner() {
        val opts = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Наведите камеру на QR-код станции")
            setBeepEnabled(false)
            setOrientationLocked(true)
        }
        scanLauncher.launch(opts)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cameraOk = grants[Manifest.permission.CAMERA] == true
        if (cameraOk) launchScanner()
        else viewModel.onScanned("")  // покажет ошибку
    }

    fun requestAndScan() {
        val camera = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
        if (camera == PackageManager.PERMISSION_GRANTED) {
            launchScanner()
        } else {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR-станции") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isProcessing) {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val result = state.lastScan
            if (result == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(
                            Icons.Default.QrCodeScanner, null,
                            tint = PathfinderBlue,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Сканируйте QR-код на станции",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "У входа в зал, на городских точках, в библиотеке — ищите наклейки с QR-кодом Pathfinder Hub.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { requestAndScan() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сканировать QR")
                }

                OutlinedButton(
                    onClick = { showManualDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ввести код вручную (отладка)")
                }
            } else {
                ResultCard(
                    result = result,
                    onScanMore = { viewModel.reset() },
                    onFinish = onBack,
                )
            }

            state.errorMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        msg,
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    if (showManualDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("ID станции") },
            text = {
                Column {
                    Text("Введите ID станции вручную (для тестов)")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualId,
                        onValueChange = { manualId = it },
                        label = { Text("station id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = manualId.trim()
                        if (id.isNotEmpty()) {
                            viewModel.onScanned(id)
                            showManualDialog = false
                            manualId = ""
                        }
                    },
                    enabled = manualId.isNotBlank()
                ) { Text("Отсканировать") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showManualDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun ResultCard(
    result: ScanResult,
    onScanMore: () -> Unit,
    onFinish: () -> Unit,
) {
    val color = if (result.geoVerified) PathfinderGreen
                else MaterialTheme.colorScheme.tertiary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.geoVerified) {
                PathfinderGreen.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (result.geoVerified) Icons.Default.CheckCircle
                                  else Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        result.stationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Тип: " + result.stationType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(result.message, style = MaterialTheme.typography.bodyLarge)

            result.distanceMeters?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Расстояние: " + it.toInt() + " м",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onScanMore,
            modifier = Modifier.weight(1f)
        ) { Text("Ещё станцию") }
        Button(
            onClick = onFinish,
            modifier = Modifier.weight(1f)
        ) { Text("Готово") }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/QrScannerScreen.kt", SCREEN)


# ============================================================
# 5. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    // === Certificates ===
    const val MY_CERTIFICATES = "my_certificates\"""",
     """    // === Certificates ===
    const val MY_CERTIFICATES = "my_certificates"

    // === QR-станции ===
    const val QR_SCANNER = "qr_scanner\""""),
], skip_if_marker='const val QR_SCANNER')


# ============================================================
# 6. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    # импорт
    ("import com.pathfinder.hub.ui.screens.certificates.MyCertificatesScreen",
     "import com.pathfinder.hub.ui.screens.certificates.MyCertificatesScreen\n"
     "import com.pathfinder.hub.ui.qr.QrScannerScreen"),

    # TeenHome — новый колбэк
    ("""                onNavigateToCertificates = { navController.navigate(Routes.MY_CERTIFICATES) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }""",
     """                onNavigateToCertificates = { navController.navigate(Routes.MY_CERTIFICATES) },
                onNavigateToQrScanner = { navController.navigate(Routes.QR_SCANNER) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }"""),

    # composable для сканера — рядом с MY_CERTIFICATES
    ("""        composable(Routes.MY_CERTIFICATES) {
            MyCertificatesScreen(onBack = { navController.popBackStack() })
        }""",
     """        composable(Routes.MY_CERTIFICATES) {
            MyCertificatesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.QR_SCANNER) {
            QrScannerScreen(onBack = { navController.popBackStack() })
        }"""),
], skip_if_marker="Routes.QR_SCANNER")


# ============================================================
# 7. TeenHomeScreen.kt — параметр + плитка
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/teen/TeenHomeScreen.kt", [
    # импорт QrCodeScanner
    ("import androidx.compose.material.icons.filled.Person",
     "import androidx.compose.material.icons.filled.Person\n"
     "import androidx.compose.material.icons.filled.QrCodeScanner"),

    # параметр
    ("""    onNavigateToCertificates: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},""",
     """    onNavigateToCertificates: () -> Unit = {},
    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},"""),

    # Ряд 4 → 3 плитки: Чаты + Сертификаты + QR
    ("""            // Кнопки разделов - Ряд 4 (Чаты + Сертификаты)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionTile(
                    icon = Icons.Default.Chat,
                    label = "Чаты",
                    onClick = onNavigateToChats,
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = Icons.Default.Description,
                    label = "Сертификаты",
                    onClick = onNavigateToCertificates,
                    modifier = Modifier.weight(1f)
                )
            }""",
     """            // Кнопки разделов - Ряд 4 (Чаты + Сертификаты + QR)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionTile(
                    icon = Icons.Default.Chat,
                    label = "Чаты",
                    onClick = onNavigateToChats,
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = Icons.Default.Description,
                    label = "Сертификаты",
                    onClick = onNavigateToCertificates,
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = Icons.Default.QrCodeScanner,
                    label = "QR",
                    onClick = onNavigateToQrScanner,
                    modifier = Modifier.weight(1f)
                )
            }"""),
], skip_if_marker="onNavigateToQrScanner")


# ============================================================
# 8. PathfinderApp.kt — seedDemoStations
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/PathfinderApp.kt", [
    # импорт
    ("import com.pathfinder.hub.data.local.entity.gamification.ChallengeEntity",
     "import com.pathfinder.hub.data.local.entity.gamification.ChallengeEntity\n"
     "import com.pathfinder.hub.data.local.entity.qr.QrStationEntity"),

    # вызов
    ("""            seedDemoData()
            seedDemoChallenges()
            seedAchievements()""",
     """            seedDemoData()
            seedDemoChallenges()
            seedDemoStations()
            seedAchievements()"""),

    # метод — вставим перед "// ==================== ДЕМО-ЧЕЛЛЕНДЖИ ===================="
    ("""    // ==================== ДЕМО-ЧЕЛЛЕНДЖИ ====================""",
     """    // ==================== ДЕМО-QR-СТАНЦИИ ====================

    /**
     * Засеивает 2 демо-станции в demo_club: entry и quest.
     * Координаты — Москва (можно ходить с телефоном).
     */
    private suspend fun seedDemoStations() {
        try {
            val db = AppDatabase.getInstance(this)
            val dao = db.qrStationDao()
            val now = java.util.Date()
            val existing = kotlinx.coroutines.flow.firstOrNull(
                dao.observeActiveByClub("demo_club")
            )
            if (!existing.isNullOrEmpty()) {
                Log.d(TAG, "QR-станции уже есть: ${'$'}{existing.size}")
                return
            }

            val demo = listOf(
                QrStationEntity(
                    id = "qr_entry_demo",
                    clubId = "demo_club",
                    eventId = null,
                    name = "Вход в зал (демо)",
                    description = "Отметь посещение встречи",
                    type = "entry",
                    rewardPoints = 10,
                    rewardAchievementId = null,
                    latitude = 55.7539,
                    longitude = 37.6208,
                    radiusMeters = 200,
                    status = "active",
                    createdBy = "director_demo",
                    createdAt = now,
                    updatedAt = now,
                ),
                QrStationEntity(
                    id = "qr_quest_demo",
                    clubId = "demo_club",
                    eventId = null,
                    name = "Городской квест (демо)",
                    description = "Точка с заданием в центре",
                    type = "quest",
                    rewardPoints = 50,
                    rewardAchievementId = null,
                    latitude = 55.7600,
                    longitude = 37.6300,
                    radiusMeters = 100,
                    status = "active",
                    createdBy = "director_demo",
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            demo.forEach { dao.upsertStation(it) }
            Log.d(TAG, "Сид QR-станций: ${'$'}{demo.size} шт.")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сида QR-станций", e)
        }
    }

    // ==================== ДЕМО-ЧЕЛЛЕНДЖИ ===================="""),
], skip_if_marker="seedDemoStations")


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Проверка:")
print("  1. Teen → Домой → ряд 4: 'Чаты' + 'Сертификаты' + 'QR'")
print("  2. Тап QR → экран 'QR-станции'")
print("  3. 'Сканировать QR' → запрос разрешений камеры → камера")
print("     или 'Ввести код вручную' → введи 'qr_entry_demo'")
print("  4. Получаешь результат: 'Посещение засчитано' или 'Слишком далеко'")
print("  5. Повторный скан той же станции сегодня → 'Вы уже отмечались'")
print()
print("Откат: рядом с каждым изменённым файлом .bak.<timestamp>")