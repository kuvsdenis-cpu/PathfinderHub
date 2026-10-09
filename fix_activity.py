#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Activity levels (G) - 08.10.2026
3 шкалы: требования / сообщения / посещения. Общий = среднее.
Запуск:
    python fix_activity.py
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
# 1. ActivityViewModel.kt
# ============================================================
VM = '''package com.pathfinder.hub.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.dao.HonorDao
import com.pathfinder.hub.data.local.dao.LevelDao
import com.pathfinder.hub.data.local.dao.ModerationDao
import com.pathfinder.hub.data.repository.QrStationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityScale(
    val label: String,
    val count: Int,
    val level: Int,
    val nextThreshold: Int?,
)

data class ActivityUiState(
    val isLoading: Boolean = true,
    val requirements: ActivityScale = ActivityScale("Требования", 0, 1, 5),
    val messages: ActivityScale = ActivityScale("Сообщения", 0, 1, 5),
    val visits: ActivityScale = ActivityScale("Посещения", 0, 1, 1),
    val overallLevel: Int = 1,
    val overallPercent: Float = 0f,
    val errorMessage: String? = null,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val levelDao: LevelDao,
    private val honorDao: HonorDao,
    private val moderationDao: ModerationDao,
    private val qrRepository: QrStationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(userId: String) {
        viewModelScope.launch {
            try {
                val levelReq = try { levelDao.countApprovedRequirementsByUser(userId) } catch (_: Exception) { 0 }
                val honorReq = try { honorDao.countApprovedRequirementsByUser(userId) } catch (_: Exception) { 0 }
                val reqTotal = levelReq + honorReq

                val msgTotal = try { moderationDao.countUserComments(userId) } catch (_: Exception) { 0 }

                val visitsTotal = try { qrRepository.countVerifiedVisits(userId) } catch (_: Exception) { 0 }

                val reqScale = scaleFor(reqTotal, intArrayOf(0, 5, 20, 50, 100), "Требования")
                val msgScale = scaleFor(msgTotal, intArrayOf(0, 5, 20, 50, 100), "Сообщения")
                val visitScale = scaleFor(visitsTotal, intArrayOf(0, 1, 3, 10, 25), "Посещения")

                val overall = ((reqScale.level + msgScale.level + visitScale.level) / 3f)
                val overallPercent = (overall - 1f) / 4f

                _state.update {
                    it.copy(
                        isLoading = false,
                        requirements = reqScale,
                        messages = msgScale,
                        visits = visitScale,
                        overallLevel = overall.toInt().coerceIn(1, 5),
                        overallPercent = overallPercent.coerceIn(0f, 1f),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    /**
     * thresholds: массив [0, 5, 20, 50, 100]. Возвращает уровень 1..5.
     */
    private fun scaleFor(count: Int, thresholds: IntArray, label: String): ActivityScale {
        var level = 1
        for (i in thresholds.indices) {
            if (count >= thresholds[i]) level = i + 1
        }
        level = level.coerceIn(1, 5)
        val next = if (level < 5) thresholds[level] else null
        return ActivityScale(label, count, level, next)
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/activity/ActivityViewModel.kt", VM)


# ============================================================
# 2. ActivityScreen.kt
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.activity

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    onBack: () -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Моя активность") },
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
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------- Общий уровень ----------
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = PathfinderBlue.copy(alpha = 0.12f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Bolt, null,
                            tint = PathfinderBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Общий уровень активности",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Уровень ${state.overallLevel} из 5",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = PathfinderBlue
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.overallPercent },
                        modifier = Modifier.fillMaxWidth(),
                        color = PathfinderBlue
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Среднее от трёх шкал ниже",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- Требования ----------
            ScaleCard(
                icon = Icons.Default.CheckCircle,
                scale = state.requirements,
                color = PathfinderBlue,
            )

            // ---------- Сообщения ----------
            ScaleCard(
                icon = Icons.Default.Chat,
                scale = state.messages,
                color = PathfinderGreen,
            )

            // ---------- Посещения ----------
            ScaleCard(
                icon = Icons.Default.QrCodeScanner,
                scale = state.visits,
                color = PathfinderYellow,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScaleCard(
    icon: ImageVector,
    scale: ActivityScale,
    color: androidx.compose.ui.graphics.Color,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    scale.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Ур. ${scale.level}/5",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (scale.level - 1) / 4f },
                modifier = Modifier.fillMaxWidth(),
                color = color
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("Сейчас: ${scale.count}")
                    scale.nextThreshold?.let {
                        append(" · До следующего уровня: ${it - scale.count}")
                    } ?: append(" · Максимум")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/activity/ActivityScreen.kt", SCREEN)


# ============================================================
# 3. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    // === QR-станции ===
    const val QR_SCANNER = "qr_scanner\"""",
     """    // === QR-станции ===
    const val QR_SCANNER = "qr_scanner"

    // === Activity ===
    const val ACTIVITY = "activity\""""),
], skip_if_marker='const val ACTIVITY = "activity"')


# ============================================================
# 4. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    ("import com.pathfinder.hub.ui.qr.QrScannerScreen",
     "import com.pathfinder.hub.ui.qr.QrScannerScreen\n"
     "import com.pathfinder.hub.ui.activity.ActivityScreen"),

    ("""                onNavigateToQrScanner = { navController.navigate(Routes.QR_SCANNER) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }""",
     """                onNavigateToQrScanner = { navController.navigate(Routes.QR_SCANNER) },
                onNavigateToActivity = { navController.navigate(Routes.ACTIVITY) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }"""),

    ("""        composable(Routes.QR_SCANNER) {
            QrScannerScreen(onBack = { navController.popBackStack() })
        }""",
     """        composable(Routes.QR_SCANNER) {
            QrScannerScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ACTIVITY) {
            ActivityScreen(onBack = { navController.popBackStack() })
        }"""),
], skip_if_marker="Routes.ACTIVITY")


# ============================================================
# 5. TeenHomeScreen.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/teen/TeenHomeScreen.kt", [
    ("import androidx.compose.material.icons.filled.AccountBalance",
     "import androidx.compose.material.icons.filled.AccountBalance\n"
     "import androidx.compose.material.icons.filled.Bolt"),

    ("""    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},""",
     """    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToActivity: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},"""),

    # Ряд 5 — Активность + пусто
    ("""                ActionTile(
                    icon = Icons.Default.QrCodeScanner,
                    label = "QR",
                    onClick = onNavigateToQrScanner,
                    modifier = Modifier.weight(1f)
                )
            }""",
     """                ActionTile(
                    icon = Icons.Default.QrCodeScanner,
                    label = "QR",
                    onClick = onNavigateToQrScanner,
                    modifier = Modifier.weight(1f)
                )
            }

            // Кнопки разделов - Ряд 5 (Активность)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionTile(
                    icon = Icons.Default.Bolt,
                    label = "Активность",
                    onClick = onNavigateToActivity,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }"""),
], skip_if_marker="onNavigateToActivity")


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Проверка:")
print("  1. Teen → Домой → ряд 5: 'Активность'")
print("  2. Тап → экран 'Моя активность'")
print("  3. 3 шкалы: Требования, Сообщения, Посещения")
print("  4. Общий уровень = среднее трёх")
print()
print("Шкалы:")
print("  Требования/Сообщения: 0/5/20/50/100")
print("  Посещения: 0/1/3/10/25")