#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Parent N1 (kids + progress) - 09.10.2026
Запуск:
    python fix_parent_n1.py
"""

import shutil, sys
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


def rewrite(path, content):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[REWRITE] " + path)


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
# 1. ParentRepository.kt
# ============================================================
REPO = '''package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.ParentDao
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.parent.ParentMeetingEntity
import com.pathfinder.hub.data.local.entity.parent.ParentMessageEntity
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParentRepository @Inject constructor(
    private val dao: ParentDao,
) {

    // ---------- Дети ----------
    fun observeChildren(parentId: String): Flow<List<ParentChildLinkEntity>> =
        dao.observeChildren(parentId)

    fun observeParents(childId: String): Flow<List<ParentChildLinkEntity>> =
        dao.observeParents(childId)

    suspend fun upsertLink(l: ParentChildLinkEntity) = dao.upsertLink(l)

    suspend fun revokeLink(parentId: String, childId: String, reason: String?) =
        dao.revokeLink(parentId, childId, System.currentTimeMillis(), reason)

    // ---------- Оплаты ----------
    fun observeChildPayments(childId: String): Flow<List<PaymentEntity>> =
        dao.observeChildPayments(childId)

    fun observeParentPayments(parentId: String): Flow<List<PaymentEntity>> =
        dao.observeParentPayments(parentId)

    suspend fun upsertPayment(p: PaymentEntity) = dao.upsertPayment(p)

    suspend fun confirmPayment(id: String, status: String, by: String) =
        dao.confirmPayment(id, status, by, System.currentTimeMillis())

    // ---------- Сообщения ----------
    fun observeMessages(parentId: String): Flow<List<ParentMessageEntity>> =
        dao.observeMessages(parentId)

    fun observePendingMessages(): Flow<List<ParentMessageEntity>> =
        dao.observePendingMessages()

    suspend fun upsertMessage(m: ParentMessageEntity) = dao.upsertMessage(m)

    suspend fun moderateMessage(id: String, status: String, by: String) =
        dao.moderateMessage(id, status, by, System.currentTimeMillis())

    // ---------- Встречи ----------
    suspend fun upsertMeeting(m: ParentMeetingEntity) = dao.upsertMeeting(m)
}
'''
if (ROOT / "app/src/main/java/com/pathfinder/hub/data/repository/ParentRepository.kt").exists():
    rewrite("app/src/main/java/com/pathfinder/hub/data/repository/ParentRepository.kt", REPO)
else:
    write_new("app/src/main/java/com/pathfinder/hub/data/repository/ParentRepository.kt", REPO)


# ============================================================
# 2. ParentHomeViewModel.kt
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.home.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.AuthRepository
import com.pathfinder.hub.data.repository.FcmTokenRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChildSummary(
    val child: UserEntity?,
    val link: ParentChildLinkEntity,
    val completedLevels: Int,
    val earnedAchievements: Int,
)

data class ParentHomeUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val children: List<ChildSummary> = emptyList(),
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class ParentHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val parentRepository: ParentRepository,
    private val levelRepository: LevelRepository,
    private val achievementRepository: AchievementRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentHomeUiState())
    val state: StateFlow<ParentHomeUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            observeUser(uid)
            observeChildren(uid)
        }
    }

    private fun observeUser(uid: String) {
        viewModelScope.launch {
            userRepository.observeUser(uid).collect { u ->
                _state.update { it.copy(user = u, isLoading = false) }
            }
        }
    }

    private fun observeChildren(parentId: String) {
        viewModelScope.launch {
            parentRepository.observeChildren(parentId).collect { links ->
                val active = links.filter { it.status == "active" }
                val summaries = active.map { link ->
                    val child = userRepository.getUser(link.childId)
                    val completions = try {
                        levelRepository.observeCompletions(link.childId)
                    } catch (_: Exception) { null }
                    // Для счётчиков используем snapshot-once
                    val levels = try {
                        levelRepository.observeCompletions(link.childId)
                    } catch (_: Exception) { null }
                    ChildSummary(
                        child = child,
                        link = link,
                        completedLevels = 0,   // подтянем отдельным flow ниже
                        earnedAchievements = 0,
                    )
                }
                _state.update { it.copy(children = summaries, isLoading = false) }

                // Подписки на счётчики каждого ребёнка
                active.forEach { link ->
                    launch {
                        levelRepository.observeCompletions(link.childId).collect { lc ->
                            _state.update { s ->
                                s.copy(children = s.children.map { cs ->
                                    if (cs.link.childId == link.childId)
                                        cs.copy(completedLevels = lc.size)
                                    else cs
                                })
                            }
                        }
                    }
                    launch {
                        achievementRepository.observeEarned(link.childId).collect { a ->
                            _state.update { s ->
                                s.copy(children = s.children.map { cs ->
                                    if (cs.link.childId == link.childId)
                                        cs.copy(earnedAchievements = a.size)
                                    else cs
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    fun logout() {
        val uid = sessionManager.getUserId()
        sessionManager.clear()
        _state.update { it.copy(loggedOut = true) }
        viewModelScope.launch {
            try { authRepository.signOut() } catch (_: Exception) {}
            if (uid != null) {
                try { fcmTokenRepository.removeCurrentToken(uid) } catch (_: Exception) {}
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/parent/ParentHomeViewModel.kt", VM)


# ============================================================
# 3. ParentHomeScreen.kt — REWRITE
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.screens.home.parent

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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeScreen(
    onNavigateToEvents: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onOpenChild: (String) -> Unit = {},
    onNavigateToPayments: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToMeetings: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: ParentHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLogout()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Родительская панель") },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(
                            Icons.Default.Notifications, "Уведомления",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout, "Выйти",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            Text(
                "Здравствуйте, ${state.user?.firstName ?: "Родитель"}!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // ---------- Дети ----------
            Text(
                "Мои дети",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (state.children.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Пока нет привязанных детей",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Попросите директора клуба отправить вам запрос на привязку",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                state.children.forEach { cs ->
                    ChildCard(
                        cs = cs,
                        onClick = { onOpenChild(cs.link.childId) }
                    )
                }
            }

            // ---------- Разделы ----------
            Text(
                "Разделы",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.CalendarMonth, "Расписание", "События клуба", onNavigateToEvents, Modifier.weight(1f))
                Tile(Icons.Default.AccountBalance, "Оплата", "Взносы", onNavigateToPayments, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Email, "Сообщения", "От клуба", onNavigateToMessages, Modifier.weight(1f))
                Tile(Icons.Default.Event, "Встречи", "С родителями", onNavigateToMeetings, Modifier.weight(1f))
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
}

@Composable
private fun ChildCard(cs: ChildSummary, onClick: () -> Unit) {
    val name = cs.child?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Ребёнок"
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Person, null,
                tint = PathfinderBlue,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Ступеней: ${cs.completedLevels} · Достижений: ${cs.earnedAchievements}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, onClick = onClick) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = PathfinderBlue, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/parent/ParentHomeScreen.kt", SCREEN)


# ============================================================
# 4. ChildProgressViewModel.kt
# ============================================================
CHILD_VM = '''package com.pathfinder.hub.ui.screens.home.parent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChildProgressUiState(
    val isLoading: Boolean = true,
    val child: UserEntity? = null,
    val levelCompletions: List<LevelCompletionEntity> = emptyList(),
    val honorCompletions: List<HonorCompletionEntity> = emptyList(),
    val achievements: List<AchievementProgressEntity> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ChildProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val childId: String = savedStateHandle.get<String>("childId") ?: ""

    private val _state = MutableStateFlow(ChildProgressUiState())
    val state: StateFlow<ChildProgressUiState> = _state.asStateFlow()

    init {
        if (childId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не указан ребёнок") }
        } else {
            load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val child = userRepository.getUser(childId)
            _state.update { it.copy(child = child, isLoading = false) }
        }
        viewModelScope.launch {
            levelRepository.observeCompletions(childId).collect { list ->
                _state.update { it.copy(levelCompletions = list) }
            }
        }
        viewModelScope.launch {
            honorRepository.observeCompletions(childId).collect { list ->
                _state.update { it.copy(honorCompletions = list) }
            }
        }
        viewModelScope.launch {
            achievementRepository.observeEarned(childId).collect { list ->
                _state.update { it.copy(achievements = list) }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/parent/ChildProgressViewModel.kt", CHILD_VM)


# ============================================================
# 5. ChildProgressScreen.kt
# ============================================================
CHILD_SCREEN = '''package com.pathfinder.hub.ui.screens.home.parent

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildProgressScreen(
    onBack: () -> Unit,
    viewModel: ChildProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val fmt = SimpleDateFormat("d MMM yyyy", Locale("ru"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.child?.let { "${it.firstName} ${it.lastName}".trim() }
                            ?: "Прогресс ребёнка"
                    )
                },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- Сводка ----------
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = PathfinderBlue.copy(alpha = 0.12f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp)) {
                    StatCell("Ступени", state.levelCompletions.size.toString(), Modifier.weight(1f))
                    StatCell("Специализации", state.honorCompletions.size.toString(), Modifier.weight(1f))
                    StatCell("Достижения", state.achievements.size.toString(), Modifier.weight(1f))
                }
            }

            // ---------- Ступени ----------
            Section("Пройденные ступени")
            if (state.levelCompletions.isEmpty()) {
                EmptyText("Пока нет завершённых ступеней")
            } else {
                state.levelCompletions.forEach { lc ->
                    RowItem(
                        icon = Icons.Default.CheckCircle,
                        title = "Ступень " + lc.levelId,
                        subtitle = "Завершено: " + fmt.format(lc.completedAt),
                    )
                }
            }

            // ---------- Специализации ----------
            Section("Специализации")
            if (state.honorCompletions.isEmpty()) {
                EmptyText("Пока нет завершённых специализаций")
            } else {
                state.honorCompletions.forEach { hc ->
                    RowItem(
                        icon = Icons.Default.EmojiEvents,
                        title = "Специализация " + hc.honorId,
                        subtitle = "Завершено: " + fmt.format(hc.completedAt),
                    )
                }
            }

            // ---------- Достижения ----------
            Section("Достижения")
            if (state.achievements.isEmpty()) {
                EmptyText("Пока нет достижений")
            } else {
                state.achievements.take(20).forEach { a ->
                    RowItem(
                        icon = Icons.Default.Star,
                        title = a.achievementId,
                        subtitle = a.earnedAt?.let { "Получено: " + fmt.format(it) } ?: "",
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PathfinderBlue)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PathfinderBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/parent/ChildProgressScreen.kt", CHILD_SCREEN)


# ============================================================
# 6. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    const val CONFERENCE_HOME = "conference_home\"""",
     """    const val CONFERENCE_HOME = "conference_home"
    const val CHILD_PROGRESS = "child_progress/{childId}"
    fun childProgress(childId: String) = "child_progress/$childId\""""),
], skip_if_marker='const val CHILD_PROGRESS')


# ============================================================
# 7. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    ("import com.pathfinder.hub.ui.screens.home.conference.ConferenceHomeScreen",
     "import com.pathfinder.hub.ui.screens.home.conference.ConferenceHomeScreen\n"
     "import com.pathfinder.hub.ui.screens.home.parent.ChildProgressScreen"),

    ("""        // === Parent ===
        composable(Routes.PARENT_HOME) {
            ParentHomeScreen(
                onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }""",
     """        // === Parent ===
        composable(Routes.PARENT_HOME) {
            ParentHomeScreen(
                onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenChild = { childId ->
                    navController.navigate(Routes.childProgress(childId))
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.CHILD_PROGRESS,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) {
            ChildProgressScreen(onBack = { navController.popBackStack() })
        }"""),
], skip_if_marker="Routes.CHILD_PROGRESS")


print()
print("=" * 60)
print("N1 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Что сделано:")
print("  - ParentRepository (обёртка над ParentDao)")
print("  - ParentHomeViewModel + ParentHomeScreen — список детей с прогрессом")
print("  - ChildProgressViewModel + ChildProgressScreen — детальный прогресс")
print("  - Routes.CHILD_PROGRESS + NavHost")
print()
print("Проверка:")
print("  1. Вход под parent → 'Мои дети' (или пусто)")
print("  2. Если привязан ребёнок (через директора) → карточка с прогрессом")
print("  3. Тап на ребёнка → ChildProgressScreen")
print()
print("Привязка ребёнка: директор → Участники → (надо добавить кнопку")
print("'Привязать родителя' — сделаю в N2 или отдельно).")
print()
print("Откат: .bak.<timestamp>")