#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Roles M (instructor, secretary, parent, conference_admin) - 09.10.2026

- NEW: InstructorHomeScreen + SecretaryHomeScreen + ParentHomeScreen + ConferenceHomeScreen
- PATCH: RoleHomeScreen — маршрутизация по ролям
- PATCH: Routes — уже есть константы (INSTRUCTOR_HOME, SECRETARY_HOME, PARENT_HOME, CONFERENCE_HOME)
- PATCH: PathfinderNavHost — 4 composable

Запуск:
    python fix_roles_m.py
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
# 0. Общий UserHeaderVm — упрощённое получение имени/клуба
# ============================================================
HEADER_VM = '''package com.pathfinder.hub.ui.screens.home.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.AuthRepository
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.FcmTokenRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeHeaderUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val clubName: String? = null,
    val clubLocation: String? = null,
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
)

/**
 * Общий VM для "домашних" экранов ролей (instructor / secretary / parent).
 * Даёт user + клуб + logout.
 */
@HiltViewModel
class HomeHeaderViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeHeaderUiState())
    val state: StateFlow<HomeHeaderUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
        } else {
            viewModelScope.launch {
                userRepository.observeUser(uid).collect { u ->
                    val club = u?.clubId?.let {
                        try { clubRepository.getClub(it) } catch (_: Exception) { null }
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            user = u,
                            clubName = club?.name,
                            clubLocation = club?.city,
                        )
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
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/common/HomeHeaderViewModel.kt", HEADER_VM)


# ============================================================
# 1. InstructorHomeScreen
# ============================================================
INSTRUCTOR = '''package com.pathfinder.hub.ui.screens.home.instructor

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TaskAlt
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
import com.pathfinder.hub.ui.screens.home.common.HomeHeaderViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorHomeScreen(
    onNavigateToApprovals: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToModeration: () -> Unit,
    onNavigateToClubChat: () -> Unit,
    onNavigateToChats: () -> Unit,
    onNavigateToEvents: () -> Unit,
    onNavigateToQrStations: () -> Unit,
    onNavigateToHomework: () -> Unit,
    onNavigateToReviewHomework: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeHeaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLogout()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Панель наставника") },
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
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
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
                "Привет, ${state.user?.firstName ?: "Наставник"}!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Ваш клуб",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.clubName ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!state.clubLocation.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(state.clubLocation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Text(
                "Работа с клубом",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.CheckCircle, "Проверка ответов", "Открытые задания", onNavigateToApprovals, Modifier.weight(1f))
                Tile(Icons.Default.FactCheck, "Проверка домашки", "Сданные домашки", onNavigateToReviewHomework, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Shield, "Модерация", "Проверка сообщений", onNavigateToModeration, Modifier.weight(1f))
                Tile(Icons.Default.Group, "Участники", "Список членов клуба", onNavigateToMembers, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Assignment, "Домашка", "Назначить", onNavigateToHomework, Modifier.weight(1f))
                Tile(Icons.Default.Event, "События", "Расписание", onNavigateToEvents, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.QrCode, "QR-станции", "Просмотр", onNavigateToQrStations, Modifier.weight(1f))
                Tile(Icons.Default.Chat, "Чат клуба", "Обсуждение", onNavigateToClubChat, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
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
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/instructor/InstructorHomeScreen.kt", INSTRUCTOR)


# ============================================================
# 2. SecretaryHomeScreen
# ============================================================
SECRETARY = '''package com.pathfinder.hub.ui.screens.home.secretary

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Card
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
import com.pathfinder.hub.ui.screens.home.common.HomeHeaderViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretaryHomeScreen(
    onNavigateToInvites: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToEvents: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToClubChat: () -> Unit,
    onNavigateToChats: () -> Unit,
    onNavigateToHomework: () -> Unit,
    onNavigateToReviewHomework: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeHeaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLogout()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Панель секретаря") },
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
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
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
                "Привет, ${state.user?.firstName ?: "Секретарь"}!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Ваш клуб",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.clubName ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!state.clubLocation.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(state.clubLocation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Text(
                "Администрирование",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.PersonAdd, "Инвайты", "Создать и управлять", onNavigateToInvites, Modifier.weight(1f))
                Tile(Icons.Default.Group, "Участники", "Список членов клуба", onNavigateToMembers, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Event, "События", "Расписание", onNavigateToEvents, Modifier.weight(1f))
                Tile(Icons.Default.Assessment, "Отчёты", "История", onNavigateToReports, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Assignment, "Домашка", "Назначить", onNavigateToHomework, Modifier.weight(1f))
                Tile(Icons.Default.FactCheck, "Проверка домашки", "Сданные домашки", onNavigateToReviewHomework, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Assignment, "Задания", "Назначить", onNavigateToTasks, Modifier.weight(1f))
                Tile(Icons.Default.Chat, "Чат клуба", "Обсуждение", onNavigateToClubChat, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
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
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/secretary/SecretaryHomeScreen.kt", SECRETARY)


# ============================================================
# 3. ParentHomeScreen (заготовка)
# ============================================================
PARENT = '''package com.pathfinder.hub.ui.screens.home.parent

import androidx.compose.foundation.layout.Arrangement
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
import com.pathfinder.hub.ui.screens.home.common.HomeHeaderViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeScreen(
    onNavigateToEvents: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: HomeHeaderViewModel = hiltViewModel(),
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
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Клуб ребёнка",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.clubName ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "Разделы",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Person, "Мой ребёнок", "Прогресс и профиль", {}, Modifier.weight(1f))
                Tile(Icons.Default.CalendarMonth, "Расписание", "События клуба", onNavigateToEvents, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Email, "Сообщения", "От клуба", {}, Modifier.weight(1f))
                Tile(Icons.Default.CalendarMonth, "Встречи", "С родителями", {}, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.AccountBalance, "Оплата взносов", "Финансы", {}, Modifier.weight(1f))
                Tile(Icons.Default.Event, "Согласия", "На мероприятия", {}, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Эти разделы в разработке. Скоро откроются.",
                    Modifier.padding(12.dp),
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
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/parent/ParentHomeScreen.kt", PARENT)


# ============================================================
# 4. ConferenceHomeScreen (заготовка)
# ============================================================
CONF = '''package com.pathfinder.hub.ui.screens.home.conference

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.pathfinder.hub.ui.screens.home.common.HomeHeaderViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceHomeScreen(
    onNavigateToNotifications: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: HomeHeaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLogout()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Конференция") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Администратор конференции",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Business, "Клубы", "Управление", {}, Modifier.weight(1f))
                Tile(Icons.Default.People, "Персонал", "Директора и наставники", {}, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Analytics, "Аналитика", "По конференции", {}, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Разделы конференции в разработке.",
                    Modifier.padding(12.dp),
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
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/conference/ConferenceHomeScreen.kt", CONF)


# ============================================================
# 5. RoleHomeScreen.kt — REWRITE
# ============================================================
ROLE_HOME = '''package com.pathfinder.hub.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pathfinder.hub.ui.navigation.Routes

@Composable
fun RoleHomeScreen(
    navController: NavHostController,
    viewModel: RoleRouterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.role) {
        if (!state.isLoading && state.role != null) {
            val route = when (state.role) {
                "director" -> Routes.DIRECTOR_HOME
                "instructor" -> Routes.INSTRUCTOR_HOME
                "secretary" -> Routes.SECRETARY_HOME
                "parent" -> Routes.PARENT_HOME
                "conference_admin" -> Routes.CONFERENCE_HOME
                "teen" -> Routes.TEEN_HOME
                else -> Routes.TEEN_HOME
            }

            navController.navigate(route) {
                popUpTo(Routes.ROLE_HOME) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else if (state.errorMessage != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Ошибка: ${state.errorMessage}",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }) {
                    Text("Вернуться ко входу")
                }
            }
        } else {
            CircularProgressIndicator()
        }
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/RoleHomeScreen.kt", ROLE_HOME)


# ============================================================
# 6. PathfinderNavHost.kt — 4 composable
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    ("import com.pathfinder.hub.ui.screens.home.teen.TeenHomeScreen",
     "import com.pathfinder.hub.ui.screens.home.teen.TeenHomeScreen\n"
     "import com.pathfinder.hub.ui.screens.home.instructor.InstructorHomeScreen\n"
     "import com.pathfinder.hub.ui.screens.home.secretary.SecretaryHomeScreen\n"
     "import com.pathfinder.hub.ui.screens.home.parent.ParentHomeScreen\n"
     "import com.pathfinder.hub.ui.screens.home.conference.ConferenceHomeScreen"),

    # после composable(Routes.ROLE_HOME)
    ("""        // === Role Router ===
        composable(Routes.ROLE_HOME) {
            RoleHomeScreen(navController = navController)
        }""",
     """        // === Role Router ===
        composable(Routes.ROLE_HOME) {
            RoleHomeScreen(navController = navController)
        }

        // === Instructor ===
        composable(Routes.INSTRUCTOR_HOME) {
            InstructorHomeScreen(
                onNavigateToApprovals = { navController.navigate(Routes.APPROVALS) },
                onNavigateToMembers = { navController.navigate(Routes.CLUB_MEMBERS) },
                onNavigateToModeration = { navController.navigate(Routes.MODERATION) },
                onNavigateToClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                onNavigateToQrStations = { navController.navigate(Routes.MY_STATIONS) },
                onNavigateToHomework = { navController.navigate(Routes.CREATE_HOMEWORK) },
                onNavigateToReviewHomework = { navController.navigate(Routes.REVIEW_HOMEWORK) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // === Secretary ===
        composable(Routes.SECRETARY_HOME) {
            SecretaryHomeScreen(
                onNavigateToInvites = { navController.navigate(Routes.DIRECTOR_INVITES) },
                onNavigateToMembers = { navController.navigate(Routes.CLUB_MEMBERS) },
                onNavigateToReports = { navController.navigate(Routes.DIRECTOR_REPORTS) },
                onNavigateToEvents = { navController.navigate(Routes.CLUB_EVENTS) },
                onNavigateToTasks = { navController.navigate(Routes.CREATE_TASK) },
                onNavigateToClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToHomework = { navController.navigate(Routes.CREATE_HOMEWORK) },
                onNavigateToReviewHomework = { navController.navigate(Routes.REVIEW_HOMEWORK) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // === Parent ===
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
        }

        // === Conference ===
        composable(Routes.CONFERENCE_HOME) {
            ConferenceHomeScreen(
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }"""),
], skip_if_marker="Routes.INSTRUCTOR_HOME")


print()
print("=" * 60)
print("M (roles) готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Что сделано:")
print("  - HomeHeaderViewModel (общий для instructor/secretary/parent/conference)")
print("  - InstructorHomeScreen: проверка ответов, домашка, модерация,")
print("      участники, события, QR, чаты")
print("  - SecretaryHomeScreen: инвайты, участники, события, отчёты,")
print("      задания, домашка, чаты")
print("  - ParentHomeScreen (заготовка): ребёнок, расписание, встречи,")
print("      оплата, согласия")
print("  - ConferenceHomeScreen (заготовка): клубы, персонал, аналитика")
print("  - RoleHomeScreen: маршрутизация по всем ролям")
print("  - PathfinderNavHost: 4 composable")
print()
print("ВАЖНО: правила Firestore уже поддерживают isClubStaff()")
print("(director, secretary, instructor). Права со-директора через hasPerm —")
print("тоже работают для instructor/secretary через hasPermOrStaff().")
print()
print("Проверка:")
print("  - Регистрация instructor по инвайту → попадает на InstructorHomeScreen")
print("  - Регистрация secretary → SecretaryHomeScreen")
print("  - Регистрация parent → ParentHomeScreen")
print("  - Автологин под каждой ролью работает через RoleHomeScreen")
print()
print("Откат: .bak.<timestamp>")