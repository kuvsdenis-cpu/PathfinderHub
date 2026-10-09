#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Homework review (director approves) - 08.10.2026
Запуск:
    python fix_homework_review.py
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
# 1. AchievementDao.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/dao/AchievementDao.kt", [
    ("""    @Query("SELECT * FROM homework WHERE childId = :childId")
    fun observeHomework(childId: String): Flow<List<HomeworkEntity>>""",
     """    @Query("SELECT * FROM homework WHERE childId = :childId")
    fun observeHomework(childId: String): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework WHERE clubId = :clubId AND status = 'done' ORDER BY createdAt DESC")
    fun observePendingHomeworkByClub(clubId: String): Flow<List<HomeworkEntity>>"""),
], skip_if_marker="observePendingHomeworkByClub")


# ============================================================
# 2. AchievementRepository.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/repository/AchievementRepository.kt", [
    ("""    fun observeHomework(c: String): Flow<List<HomeworkEntity>> = dao.observeHomework(c)
    suspend fun upsertHomework(h: HomeworkEntity) = dao.upsertHomework(h)""",
     """    fun observeHomework(c: String): Flow<List<HomeworkEntity>> = dao.observeHomework(c)
    suspend fun upsertHomework(h: HomeworkEntity) = dao.upsertHomework(h)

    fun observePendingHomeworkByClub(clubId: String): Flow<List<HomeworkEntity>> =
        dao.observePendingHomeworkByClub(clubId)

    suspend fun approveHomework(h: HomeworkEntity) =
        dao.upsertHomework(h.copy(
            status = "approved",
            approvedAt = java.util.Date(),
            pendingSync = true,
        ))

    suspend fun rejectHomework(h: HomeworkEntity) =
        dao.upsertHomework(h.copy(
            status = "rejected",
            approvedAt = null,
            pendingSync = true,
        ))"""),
], skip_if_marker="approveHomework")


# ============================================================
# 3. ReviewHomeworkViewModel.kt
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.homework.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewHomeworkUiState(
    val isLoading: Boolean = true,
    val items: List<HomeworkEntity> = emptyList(),
    val childNames: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ReviewHomeworkViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewHomeworkUiState())
    val state: StateFlow<ReviewHomeworkUiState> = _state.asStateFlow()

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
                val user = userRepository.getUser(userId)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                achievementRepository.observePendingHomeworkByClub(clubId).collect { list ->
                    // Подтягиваем имена детей
                    val names = mutableMapOf<String, String>()
                    list.map { it.childId }.distinct().forEach { childId ->
                        val child = userRepository.getUser(childId)
                        if (child != null) {
                            names[childId] = "${child.firstName} ${child.lastName}".trim()
                        }
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            items = list,
                            childNames = names,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun approve(hw: HomeworkEntity) {
        viewModelScope.launch {
            try {
                achievementRepository.approveHomework(hw)
                _state.update { it.copy(message = "Одобрено: " + hw.title) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun reject(hw: HomeworkEntity) {
        viewModelScope.launch {
            try {
                achievementRepository.rejectHomework(hw)
                _state.update { it.copy(message = "Возвращено: " + hw.title) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(message = null, errorMessage = null) }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/homework/review/ReviewHomeworkViewModel.kt", VM)


# ============================================================
# 4. ReviewHomeworkScreen.kt
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.screens.homework.review

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewHomeworkScreen(
    onBack: () -> Unit,
    viewModel: ReviewHomeworkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Проверка домашки") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        modifier = Modifier.size(48.dp),
                        tint = PathfinderGreen
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Нет сданных домашек",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Когда следопыт отметит выполнение — задание появится здесь",
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
                items(state.items, key = { it.id }) { hw ->
                    ReviewCard(
                        hw = hw,
                        childName = state.childNames[hw.childId] ?: hw.childId,
                        onApprove = { viewModel.approve(hw) },
                        onReject = { viewModel.reject(hw) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    hw: HomeworkEntity,
    childName: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School, null,
                    tint = PathfinderBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        childName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        hw.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (hw.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(hw.description, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onReject) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Вернуть")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApprove) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Одобрить")
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/homework/review/ReviewHomeworkScreen.kt", SCREEN)


# ============================================================
# 5. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    const val CREATE_HOMEWORK = "create_homework\"""",
     """    const val CREATE_HOMEWORK = "create_homework"
    const val REVIEW_HOMEWORK = "review_homework\""""),
], skip_if_marker='const val REVIEW_HOMEWORK')


# ============================================================
# 6. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    ("import com.pathfinder.hub.ui.screens.homework.CreateHomeworkScreen",
     "import com.pathfinder.hub.ui.screens.homework.CreateHomeworkScreen\n"
     "import com.pathfinder.hub.ui.screens.homework.review.ReviewHomeworkScreen"),

    # колбэк в DirectorHome
    ("""                onNavigateToQrStations = { navController.navigate(Routes.MY_STATIONS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },""",
     """                onNavigateToQrStations = { navController.navigate(Routes.MY_STATIONS) },
                onNavigateToHomework = { navController.navigate(Routes.CREATE_HOMEWORK) },
                onNavigateToReviewHomework = { navController.navigate(Routes.REVIEW_HOMEWORK) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },"""),

    # composable после CREATE_STATION
    ("""        composable(Routes.CREATE_STATION) {
            CreateStationScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }""",
     """        composable(Routes.CREATE_STATION) {
            CreateStationScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.CREATE_HOMEWORK) {
            CreateHomeworkScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.REVIEW_HOMEWORK) {
            ReviewHomeworkScreen(onBack = { navController.popBackStack() })
        }"""),
], skip_if_marker="Routes.REVIEW_HOMEWORK")


# ============================================================
# 7. DirectorHomeScreen.kt — 7-й ряд
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeScreen.kt", [
    ("""    onNavigateToQrStations: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,""",
     """    onNavigateToQrStations: () -> Unit = {},
    onNavigateToHomework: () -> Unit = {},
    onNavigateToReviewHomework: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,"""),

    ("""            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DirectorActionCard(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
                DirectorActionCard(Icons.Default.QrCode, "QR-станции", "Точки для сканирования", onNavigateToQrStations, Modifier.weight(1f))
            }""",
     """            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DirectorActionCard(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
                DirectorActionCard(Icons.Default.QrCode, "QR-станции", "Точки для сканирования", onNavigateToQrStations, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DirectorActionCard(Icons.Default.Assignment, "Домашка", "Назначить следопытам", onNavigateToHomework, Modifier.weight(1f))
                DirectorActionCard(Icons.Default.FactCheck, "Проверка", "Сданные домашки", onNavigateToReviewHomework, Modifier.weight(1f))
            }"""),
], skip_if_marker="onNavigateToReviewHomework")


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Проверка:")
print("  Teen:")
print("    1. Домой → Мои задачи → Домашка")
print("    2. Отметить выполненным → статус 'Отправлено на проверку'")
print("  Директор:")
print("    3. Главная → 'Проверка' (7-й ряд)")
print("    4. Видит сданную домашку + имя следопыта")
print("    5. 'Одобрить' → у teen статус 'Проверено'")
print("       'Вернуть' → у teen статус 'rejected'")
print()
print("Откат: рядом с каждым .bak.<timestamp>")