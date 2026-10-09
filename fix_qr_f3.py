#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QR-stations F3 (director) - 08.10.2026

- NEW: StationQrHelper.kt (генерация QR-PNG)
- NEW: ManageStationsViewModel.kt + ManageStationsScreen.kt
- NEW: CreateStationViewModel.kt + CreateStationScreen.kt
- PATCH: Routes (MY_STATIONS, CREATE_STATION)
- PATCH: PathfinderNavHost (2 composable + 1 колбэк в DirectorHome)
- PATCH: DirectorHomeScreen (карточка "QR-станции")

Запуск:
    python fix_qr_f3.py
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
        print("[SKIP] " + path + " (уже есть)")
        return
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
# 1. StationQrHelper.kt
# ============================================================
QR_HELPER = '''package com.pathfinder.hub.ui.qr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/**
 * Генератор QR-PNG для станции.
 * Payload: "PFHUB:STATION:<stationId>".
 * Файл пишется в cacheDir/qr_codes/station_<id>.png.
 */
object StationQrHelper {

    private const val AUTHORITY = "com.pathfinder.hub.fileprovider"
    private const val TAG = "StationQrHelper"

    fun payload(stationId: String): String = "PFHUB:STATION:$stationId"

    /**
     * Генерирует PNG с QR-кодом. Возвращает File.
     */
    fun generatePng(context: Context, stationId: String, sizePx: Int = 800): File {
        val dir = File(context.cacheDir, "qr_codes").apply { mkdirs() }
        val file = File(dir, "station_$stationId.png")

        val text = payload(stationId)
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    /**
     * Поделиться PNG через ACTION_SEND.
     */
    fun share(context: Context, stationName: String, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "QR-станция: $stationName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться QR"))
        } catch (e: Exception) {
            Log.e(TAG, "share failed", e)
        }
    }

    /**
     * Открыть PNG в системном просмотрщике.
     */
    fun open(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Открыть QR"))
        } catch (e: Exception) {
            Log.e(TAG, "open failed", e)
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/StationQrHelper.kt", QR_HELPER)


# ============================================================
# 2. ManageStationsViewModel.kt
# ============================================================
MANAGE_VM = '''package com.pathfinder.hub.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.repository.QrStationRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageStationsUiState(
    val isLoading: Boolean = true,
    val stations: List<QrStationEntity> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ManageStationsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrRepository: QrStationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageStationsUiState())
    val state: StateFlow<ManageStationsUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            viewModelScope.launch {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                qrRepository.observeAllStations(clubId).collect { list ->
                    _state.update { it.copy(isLoading = false, stations = list) }
                }
            }
        }
    }

    fun deleteStation(id: String) {
        viewModelScope.launch {
            try { qrRepository.deleteStation(id) }
            catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/ManageStationsViewModel.kt", MANAGE_VM)


# ============================================================
# 3. ManageStationsScreen.kt
# ============================================================
MANAGE_SCREEN = '''package com.pathfinder.hub.ui.qr

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageStationsScreen(
    onBack: () -> Unit,
    onCreateStation: () -> Unit,
    viewModel: ManageStationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateStation,
                containerColor = PathfinderBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, "Создать станцию")
            }
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.stations.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.QrCode, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Пока нет станций",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Нажмите + чтобы создать первую",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.stations, key = { it.id }) { s ->
                    StationCard(
                        s = s,
                        onShowQr = {
                            scope.launch {
                                val f = withContext(Dispatchers.IO) {
                                    StationQrHelper.generatePng(ctx, s.id)
                                }
                                StationQrHelper.open(ctx, f)
                            }
                        },
                        onShareQr = {
                            scope.launch {
                                val f = withContext(Dispatchers.IO) {
                                    StationQrHelper.generatePng(ctx, s.id)
                                }
                                StationQrHelper.share(ctx, s.name, f)
                            }
                        },
                        onCopyPayload = {
                            Toast.makeText(
                                ctx,
                                StationQrHelper.payload(s.id),
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onDelete = { viewModel.deleteStation(s.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StationCard(
    s: QrStationEntity,
    onShowQr: () -> Unit,
    onShareQr: () -> Unit,
    onCopyPayload: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Тип: " + s.type + " · ID: " + s.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (s.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(s.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Радиус: " + s.radiusMeters + " м · Очки: " + s.rewardPoints,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Координаты: " + s.latitude + ", " + s.longitude,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onShowQr) {
                    Icon(Icons.Default.QrCode, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("QR")
                }
                IconButton(onClick = onShareQr) {
                    Icon(Icons.Default.Share, "Поделиться", tint = PathfinderBlue)
                }
                IconButton(onClick = onCopyPayload) {
                    Icon(Icons.Default.QrCode, "Скопировать payload", tint = PathfinderBlue)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/ManageStationsScreen.kt", MANAGE_SCREEN)


# ============================================================
# 4. CreateStationViewModel.kt
# ============================================================
CREATE_VM = '''package com.pathfinder.hub.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.repository.QrStationRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class CreateStationUiState(
    val name: String = "",
    val description: String = "",
    val type: String = "entry",        // entry / quest / custom
    val rewardPoints: String = "10",
    val rewardAchievementId: String = "",
    val latitude: String = "55.7539",
    val longitude: String = "37.6208",
    val radiusMeters: String = "100",
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
    val createdId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CreateStationViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrRepository: QrStationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateStationUiState())
    val state: StateFlow<CreateStationUiState> = _state.asStateFlow()

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onType(v: String) = _state.update { it.copy(type = v) }
    fun onRewardPoints(v: String) = _state.update { it.copy(rewardPoints = v.filter { c -> c.isDigit() }) }
    fun onRewardAchievement(v: String) = _state.update { it.copy(rewardAchievementId = v) }
    fun onLatitude(v: String) = _state.update { it.copy(latitude = v) }
    fun onLongitude(v: String) = _state.update { it.copy(longitude = v) }
    fun onRadius(v: String) = _state.update { it.copy(radiusMeters = v.filter { c -> c.isDigit() }) }

    fun create() {
        val s = _state.value
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(errorMessage = "Не авторизован") }
            return
        }
        if (s.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        val lat = s.latitude.toDoubleOrNull()
        val lon = s.longitude.toDoubleOrNull()
        if (lat == null || lon == null) {
            _state.update { it.copy(errorMessage = "Неверные координаты") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                val id = "qr_" + UUID.randomUUID().toString().take(8)
                val now = Date()
                val entity = QrStationEntity(
                    id = id,
                    clubId = clubId,
                    eventId = null,
                    name = s.name.trim(),
                    description = s.description.trim(),
                    type = s.type,
                    rewardPoints = s.rewardPoints.toIntOrNull() ?: 0,
                    rewardAchievementId = s.rewardAchievementId.trim().ifBlank { null },
                    latitude = lat,
                    longitude = lon,
                    radiusMeters = s.radiusMeters.toIntOrNull() ?: 100,
                    status = "active",
                    createdBy = uid,
                    createdAt = now,
                    updatedAt = now,
                    pendingSync = true,
                )
                qrRepository.upsertStation(entity)
                _state.update { it.copy(isLoading = false, isCreated = true, createdId = id) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка")
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/CreateStationViewModel.kt", CREATE_VM)


# ============================================================
# 5. CreateStationScreen.kt
# ============================================================
CREATE_SCREEN = '''package com.pathfinder.hub.ui.qr

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStationScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateStationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая QR-станция") },
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
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            Text("Тип станции", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row {
                FilterChip(
                    selected = state.type == "entry",
                    onClick = { viewModel.onType("entry") },
                    label = { Text("Вход") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = state.type == "quest",
                    onClick = { viewModel.onType("quest") },
                    label = { Text("Квест") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = state.type == "custom",
                    onClick = { viewModel.onType("custom") },
                    label = { Text("Другое") }
                )
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.rewardPoints,
                onValueChange = viewModel::onRewardPoints,
                label = { Text("Очки за посещение") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.rewardAchievementId,
                onValueChange = viewModel::onRewardAchievement,
                label = { Text("ID ачивки (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.latitude,
                onValueChange = viewModel::onLatitude,
                label = { Text("Широта") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.longitude,
                onValueChange = viewModel::onLongitude,
                label = { Text("Долгота") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.radiusMeters,
                onValueChange = viewModel::onRadius,
                label = { Text("Радиус (м)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::create,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Создать станцию")
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/CreateStationScreen.kt", CREATE_SCREEN)


# ============================================================
# 6. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    // === Activity ===
    const val ACTIVITY = "activity\"""",
     """    // === Activity ===
    const val ACTIVITY = "activity"

    // === QR-станции (управление директором) ===
    const val MY_STATIONS = "my_stations"
    const val CREATE_STATION = "create_station\""""),
], skip_if_marker='const val MY_STATIONS')


# ============================================================
# 7. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    ("import com.pathfinder.hub.ui.activity.ActivityScreen",
     "import com.pathfinder.hub.ui.activity.ActivityScreen\n"
     "import com.pathfinder.hub.ui.qr.ManageStationsScreen\n"
     "import com.pathfinder.hub.ui.qr.CreateStationScreen"),

    # колбэк в DirectorHome
    ("""                onNavigateToContentManager = { navController.navigate(Routes.CONTENT_MANAGER) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },""",
     """                onNavigateToContentManager = { navController.navigate(Routes.CONTENT_MANAGER) },
                onNavigateToQrStations = { navController.navigate(Routes.MY_STATIONS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },"""),

    # 2 composable — после Routes.CREATE_TASK
    ("""        composable(Routes.CREATE_TASK) {
            CreateTaskScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }""",
     """        composable(Routes.CREATE_TASK) {
            CreateTaskScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.MY_STATIONS) {
            ManageStationsScreen(
                onBack = { navController.popBackStack() },
                onCreateStation = { navController.navigate(Routes.CREATE_STATION) }
            )
        }

        composable(Routes.CREATE_STATION) {
            CreateStationScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }"""),
], skip_if_marker="Routes.MY_STATIONS")


# ============================================================
# 8. DirectorHomeScreen.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeScreen.kt", [
    # параметр
    ("""    onNavigateToContentManager: () -> Unit = {},
    onNavigateToChats: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,""",
     """    onNavigateToContentManager: () -> Unit = {},
    onNavigateToChats: () -> Unit = {},
    onNavigateToQrStations: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,"""),

    # последний ряд: "Чаты" + Spacer → "Чаты" + "QR-станции"
    ("""            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DirectorActionCard(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }""",
     """            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DirectorActionCard(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
                DirectorActionCard(Icons.Default.QrCode, "QR-станции", "Точки для сканирования", onNavigateToQrStations, Modifier.weight(1f))
            }"""),
], skip_if_marker="onNavigateToQrStations")


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Проверка (директор):")
print("  1. Главная → карточка 'QR-станции' в последнем ряду")
print("  2. Тап → список станций (пусто, FAB '+')")
print("  3. FAB '+' → создание: название, тип, координаты, очки")
print("  4. После создания станция появляется в списке")
print("  5. Кнопка 'QR' → генерирует PNG и открывает в просмотрщике")
print("  6. Иконка Share → ACTION_SEND с PNG")
print()
print("Формат payload в QR: 'PFHUB:STATION:<stationId>'")