#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QR-quests Q2 (teen UI) - 09.10.2026

- QrScanViewModel: +QrQuestRepository, +activeQuests, +обработка квестов при скане
- QrScannerScreen: +секция "Мои квесты", +результат с обновлениями квестов

Запуск:
    python fix_qr_quests_q2.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def rewrite(path, content):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[REWRITE] " + path)


# ============================================================
# 1. QrScanViewModel.kt — REWRITE
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
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestProgressEntity
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.QrQuestRepository
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

data class QuestUpdate(
    val questId: String,
    val questTitle: String,
    val stepIndex: Int,
    val totalSteps: Int,
    val completed: Boolean,
    val rewardPoints: Int,
)

data class QuestWithProgress(
    val quest: QrQuestEntity,
    val lastCompletedStepIndex: Int,
    val totalSteps: Int,
    val completed: Boolean,
)

data class ScanResult(
    val ok: Boolean,
    val geoVerified: Boolean,
    val stationName: String,
    val stationType: String,
    val distanceMeters: Double?,
    val rewardPoints: Int,
    val message: String,
    val alreadyVisitedToday: Boolean = false,
    val questUpdates: List<QuestUpdate> = emptyList(),
)

data class QrScanUiState(
    val isProcessing: Boolean = false,
    val lastScan: ScanResult? = null,
    val errorMessage: String? = null,
    val activeQuests: List<QuestWithProgress> = emptyList(),
)

@HiltViewModel
class QrScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrRepository: QrStationRepository,
    private val qrQuestRepository: QrQuestRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QrScanUiState())
    val state: StateFlow<QrScanUiState> = _state.asStateFlow()

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    init {
        observeActiveQuests()
    }

    private fun observeActiveQuests() {
        val uid = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            val user = userRepository.getUser(uid)
            val clubId = user?.clubId ?: return@launch

            qrQuestRepository.observeActiveQuests(clubId).collect { quests ->
                val list = quests.map { q ->
                    val progress = qrQuestRepository.getProgress(q.id, uid)
                    val total = qrQuestRepository.getStepCount(q.id)
                    QuestWithProgress(
                        quest = q,
                        lastCompletedStepIndex = progress?.lastCompletedStepIndex ?: 0,
                        totalSteps = total,
                        completed = progress?.completedAt != null,
                    )
                }
                _state.update { it.copy(activeQuests = list) }
            }
        }
    }

    fun reset() {
        _state.update { it.copy(lastScan = null, errorMessage = null) }
    }

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

                val already = qrRepository.countVisitsToday(uid, station.id) > 0

                // ---------- Локация и визит ----------
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

                // Награда за саму станцию (ачивка)
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

                // ---------- Обработка квестов ----------
                val questUpdates = if (verified) {
                    processQuestsForStation(uid, clubId, station, already)
                } else emptyList()

                // ---------- Сообщение ----------
                val baseMsg = when {
                    already -> "Вы уже отмечались на этой станции сегодня"
                    verified -> "Посещение засчитано! +" + station.rewardPoints + " очков"
                    loc == null -> "Посещение сохранено, но гео не подтверждено (нет локации)"
                    else -> "Слишком далеко: " + distance?.toInt() + " м (нужно " +
                            station.radiusMeters + " м). Посещение сохранено как непроверенное."
                }

                val questMsg = if (questUpdates.isNotEmpty()) {
                    val parts = questUpdates.map { u ->
                        if (u.completed)
                            "🏆 Квест «${u.questTitle}» завершён! +${u.rewardPoints} очков"
                        else
                            "Квест «${u.questTitle}»: ${u.stepIndex}/${u.totalSteps}"
                    }
                    parts.joinToString(" · ")
                } else ""

                val finalMsg = listOf(baseMsg, questMsg)
                    .filter { it.isNotBlank() }
                    .joinToString("\\n")

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
                            message = finalMsg,
                            alreadyVisitedToday = already,
                            questUpdates = questUpdates,
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

    /**
     * Найти все квесты, в которые входит станция, обновить прогресс,
     * выдать награды за завершение.
     */
    private suspend fun processQuestsForStation(
        uid: String,
        clubId: String,
        station: QrStationEntity,
        alreadyVisited: Boolean,
    ): List<QuestUpdate> {
        val links = qrQuestRepository.getQuestsForStation(station.id)
        if (links.isEmpty()) return emptyList()

        val updates = mutableListOf<QuestUpdate>()
        val now = Date()

        for (link in links) {
            val quest = qrQuestRepository.getQuest(link.questId) ?: continue
            if (quest.status != "active") continue

            // Проверка дат
            val inRange = (quest.dateStart == null || !now.before(quest.dateStart)) &&
                    (quest.dateEnd == null || !now.after(quest.dateEnd))
            if (!inRange) continue

            val total = qrQuestRepository.getStepCount(quest.id)
            val existing = qrQuestRepository.getProgress(quest.id, uid)
            val last = existing?.lastCompletedStepIndex ?: 0

            // Если уже завершён — просто добавляем в результат
            if (existing?.completedAt != null) {
                continue
            }

            // Ожидаемый шаг?
            val expected = last + 1
            if (link.stepIndex != expected) {
                // Не тот шаг — пропускаем
                Log.d("QrScan", "quest ${quest.title}: step ${link.stepIndex}, expected $expected")
                continue
            }

            // Отмечаем шаг
            val newLast = link.stepIndex
            val newIndices = (existing?.completedStepIndices ?: emptyList()) + link.stepIndex
            val isCompleted = newLast >= total

            val updated = QrQuestProgressEntity(
                questId = quest.id,
                userId = uid,
                clubId = clubId,
                lastCompletedStepIndex = newLast,
                completedStepIndices = newIndices.distinct(),
                startedAt = existing?.startedAt ?: now,
                completedAt = if (isCompleted) now else null,
                rewardClaimed = existing?.rewardClaimed ?: false,
                pendingSync = true,
            )
            qrQuestRepository.upsertProgress(updated)

            // Награда за завершение квеста
            if (isCompleted) {
                if (quest.rewardAchievementId != null) {
                    try {
                        achievementRepository.upsertProgress(
                            AchievementProgressEntity(
                                userId = uid,
                                achievementId = quest.rewardAchievementId,
                                status = "earned",
                                earnedAt = now,
                                revokedAt = null,
                                revokedBy = null,
                                revokeReason = null,
                                pendingSync = false,
                            )
                        )
                    } catch (e: Exception) {
                        Log.w("QrScan", "quest achievement failed", e)
                    }
                }
            }

            updates.add(
                QuestUpdate(
                    questId = quest.id,
                    questTitle = quest.title,
                    stepIndex = newLast,
                    totalSteps = total,
                    completed = isCompleted,
                    rewardPoints = if (isCompleted) quest.rewardPoints else 0,
                )
            )
        }

        return updates
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
rewrite("app/src/main/java/com/pathfinder/hub/ui/qr/QrScanViewModel.kt", VM)


# ============================================================
# 2. QrScannerScreen.kt — REWRITE
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            } else {
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
                                "У входа в зал, на городских точках, в библиотеке - ищите наклейки с QR-кодом Pathfinder Hub.",
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

                    // ---------- Активные квесты ----------
                    if (state.activeQuests.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Мои квесты",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        state.activeQuests.forEach { qp ->
                            QuestCard(qp)
                        }
                    }
                } else {
                    ResultCard(
                        result = result,
                        onScanMore = { viewModel.reset() },
                        onFinish = onBack,
                    )
                }
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
        AlertDialog(
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
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun QuestCard(qp: QuestWithProgress) {
    val percent = if (qp.totalSteps > 0)
        qp.lastCompletedStepIndex.toFloat() / qp.totalSteps else 0f

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EmojiEvents, null,
                    tint = if (qp.completed) PathfinderGreen else PathfinderBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        qp.quest.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (qp.quest.description.isNotBlank()) {
                        Text(
                            qp.quest.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                Text(
                    "${qp.lastCompletedStepIndex}/${qp.totalSteps}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (qp.completed) PathfinderGreen else PathfinderBlue
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { percent },
                modifier = Modifier.fillMaxWidth(),
                color = if (qp.completed) PathfinderGreen else PathfinderBlue
            )
            if (qp.completed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "🏆 Завершён!",
                    style = MaterialTheme.typography.bodySmall,
                    color = PathfinderGreen,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Следующий шаг: ${qp.lastCompletedStepIndex + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
rewrite("app/src/main/java/com/pathfinder/hub/ui/qr/QrScannerScreen.kt", SCREEN)


print()
print("=" * 60)
print("Q2 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Что сделано:")
print("  - QrScanViewModel: +QrQuestRepository, +activeQuests в state,")
print("      +processQuestsForStation при скане (linear-проверка stepIndex)")
print("  - QrScannerScreen: +секция 'Мои квесты' (progress N/M),")
print("      +результат с обновлениями квестов")
print()
print("Проверка (после Q3, когда будут квесты):")
print("  1. Teen → QR → секция 'Мои квесты' с прогрессом")
print("  2. Сканирует станцию шаг 1 → 'Квест X: 1/3'")
print("  3. Сканирует станцию шаг 2 → 'Квест X: 2/3'")
print("  4. Сканирует станцию шаг 3 → '🏆 Квест X завершён!'")
print("  5. Если сканирует не тот шаг → 'Квест X: только шаг N'")
print()
print("ВАЖНО: сейчас квестов ещё нет — их создаёт директор (Q3).")
print("Пока проверить можно только отсутствие ошибок компиляции.")
print()
print("Откат: .bak.<timestamp>")