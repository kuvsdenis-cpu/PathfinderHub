#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Conference secretary CS2 (UI) - 09.10.2026

- NEW: ConferenceSecretaryHomeScreen + VM
- NEW: ConferenceClubsScreen + VM
- NEW: ConferenceMembersScreen + VM
- NEW: ConferenceInvitesScreen + VM (для conference_admin)
- PATCH: ConferenceHomeScreen — карточки
- PATCH: Routes + PathfinderNavHost
- PATCH: RoleHomeScreen — conference_secretary
- PATCH: firestore.rules — чтение users для conference staff

Запуск:
    python fix_conference_secretary_cs2.py
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
# 1. ConferenceStaffViewModel — общий для секретаря и admin
# ============================================================
STAFF_VM = '''package com.pathfinder.hub.ui.screens.home.conference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.ClubEntity
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

data class ConferenceStaffUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val conferenceName: String? = null,
    val clubs: List<ClubEntity> = emptyList(),
    val members: List<UserEntity> = emptyList(),
    val isAdmin: Boolean = false,
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
)

/**
 * Общий VM для conference_admin и conference_secretary.
 * Показывает клубы конференции и её кадры.
 */
@HiltViewModel
class ConferenceStaffViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConferenceStaffUiState())
    val state: StateFlow<ConferenceStaffUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(uid: String) {
        viewModelScope.launch {
            userRepository.observeUser(uid).collect { u ->
                if (u == null) return@collect
                val confId = u.conferenceId
                if (confId.isNullOrBlank()) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "Конференция не найдена")
                    }
                    return@collect
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        user = u,
                        isAdmin = u.role == "conference_admin",
                    )
                }

                // Подписки
                launch {
                    clubRepository.observeClubsByConference(confId).collect { list ->
                        _state.update { it.copy(clubs = list) }
                    }
                }
                launch {
                    userRepository.observeConferenceMembers(confId).collect { list ->
                        _state.update { it.copy(members = list) }
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
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/conference/ConferenceStaffViewModel.kt", STAFF_VM)


# ============================================================
# 2. ConferenceSecretaryHomeScreen
# ============================================================
SEC_HOME = '''package com.pathfinder.hub.ui.screens.home.conference

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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
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
fun ConferenceSecretaryHomeScreen(
    onNavigateToClubs: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ConferenceStaffViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLogout()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Секретарь конференции") },
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
                        "Ваша конференция",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ID: " + (state.user?.conferenceId ?: "—"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Клубов: ${state.clubs.size} · Сотрудников: ${state.members.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                "Работа с конференцией",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Business, "Клубы", "Список клубов", onNavigateToClubs, Modifier.weight(1f))
                Tile(Icons.Default.People, "Кадры", "Директора и наставники", onNavigateToMembers, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Assignment, "Мероприятия", "Скоро", {}, Modifier.weight(1f))
                Tile(Icons.Default.Analytics, "Отчёты", "Скоро", {}, Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Мероприятия и отчёты конференции — в разработке.",
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
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/conference/ConferenceSecretaryHomeScreen.kt", SEC_HOME)


# ============================================================
# 3. ConferenceClubsScreen
# ============================================================
CLUBS = '''package com.pathfinder.hub.ui.screens.home.conference

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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceClubsScreen(
    onBack: () -> Unit,
    viewModel: ConferenceStaffViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Клубы конференции") },
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
        } else if (state.clubs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text("В конференции пока нет клубов")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.clubs, key = { it.id }) { club ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Business, null,
                                tint = PathfinderBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    club.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    (club.city + ", " + club.region).trim().trim(','),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                AssistChip(
                                    onClick = {},
                                    label = { Text("ID: " + club.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/conference/ConferenceClubsScreen.kt", CLUBS)


# ============================================================
# 4. ConferenceMembersScreen
# ============================================================
MEMBERS = '''package com.pathfinder.hub.ui.screens.home.conference

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceMembersScreen(
    onBack: () -> Unit,
    viewModel: ConferenceStaffViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кадры конференции") },
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
        } else if (state.members.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text("Сотрудников пока нет")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.members, key = { it.id }) { user ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person, null,
                                tint = PathfinderBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${user.firstName} ${user.lastName}".trim(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    roleLabel(user.role),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!user.clubId.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("Клуб: " + user.clubId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "director" -> "Директор"
    "instructor" -> "Наставник"
    "secretary" -> "Секретарь клуба"
    "conference_admin" -> "Администратор конференции"
    "conference_secretary" -> "Секретарь конференции"
    else -> role
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/conference/ConferenceMembersScreen.kt", MEMBERS)


# ============================================================
# 5. ConferenceInvitesScreen + VM (для conference_admin)
# ============================================================
INV_VM = '''package com.pathfinder.hub.ui.screens.home.conference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import com.pathfinder.hub.data.repository.InviteRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.usecase.director.CreateInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConferenceInvitesUiState(
    val isLoading: Boolean = false,
    val lastCreated: InviteEntity? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class ConferenceInvitesViewModel @Inject constructor(
    private val createInviteUseCase: CreateInviteUseCase,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ConferenceInvitesUiState())
    val state: StateFlow<ConferenceInvitesUiState> = _state.asStateFlow()

    fun createSecretaryInvite() {
        val uid = sessionManager.getUserId() ?: return
        _state.update { it.copy(isLoading = true, errorMessage = null, lastCreated = null) }
        viewModelScope.launch {
            createInviteUseCase(
                role = "conference_secretary",
                clubId = null,
                permissions = emptyList(),
            ).fold(
                onSuccess = { inv ->
                    _state.update { it.copy(isLoading = false, lastCreated = inv) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка")
                    }
                }
            )
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/conference/ConferenceInvitesViewModel.kt", INV_VM)


INV_SCREEN = '''package com.pathfinder.hub.ui.screens.home.conference

import android.content.Intent
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceInvitesScreen(
    onBack: () -> Unit,
    viewModel: ConferenceInvitesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инвайты конференции") },
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Пригласить секретаря конференции",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Секретарь конференции видит все клубы конференции, кадры " +
                        "и мероприятия. Создавать инвайты может только администратор.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = viewModel::createSecretaryInvite,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp), strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Создать инвайт-код")
                }
            }

            state.lastCreated?.let { invite ->
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Новый код создан", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            invite.code,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        val fmt = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
                        Text("Действует до: " + fmt.format(invite.expiresAt))

                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Приглашение секретаря конференции Pathfinder Hub.\\n\\n" +
                                            "Код: " + invite.code + "\\n\\n" +
                                            "Введите код при регистрации в приложении."
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "Поделиться"))
                        }) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Поделиться")
                        }
                    }
                }
            }

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/conference/ConferenceInvitesScreen.kt", INV_SCREEN)


# ============================================================
# 6. ConferenceHomeScreen — rewrite с карточками
# ============================================================
CONF_HOME = '''package com.pathfinder.hub.ui.screens.home.conference

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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
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
fun ConferenceHomeScreen(
    onNavigateToClubs: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToInvites: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: ConferenceStaffViewModel = hiltViewModel(),
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
                "Администратор конференции",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Привет, ${state.user?.firstName ?: "Админ"}!",
                style = MaterialTheme.typography.bodyLarge
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "ID конференции: " + (state.user?.conferenceId ?: "—"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Клубов: ${state.clubs.size} · Сотрудников: ${state.members.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Business, "Клубы", "Список клубов", onNavigateToClubs, Modifier.weight(1f))
                Tile(Icons.Default.People, "Кадры", "Директора и наставники", onNavigateToMembers, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.PersonAdd, "Инвайты", "Секретарь конференции", onNavigateToInvites, Modifier.weight(1f))
                Tile(Icons.Default.Assignment, "Мероприятия", "Скоро", {}, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Default.Analytics, "Аналитика", "Скоро", {}, Modifier.weight(1f))
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
                    "Мероприятия и аналитика конференции — в разработке.",
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
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/conference/ConferenceHomeScreen.kt", CONF_HOME)


# ============================================================
# 7. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    const val CONFERENCE_HOME = "conference_home\"""",
     """    const val CONFERENCE_HOME = "conference_home"
    const val CONFERENCE_SECRETARY_HOME = "conference_secretary_home"
    const val CONFERENCE_CLUBS = "conference_clubs"
    const val CONFERENCE_MEMBERS = "conference_members"
    const val CONFERENCE_INVITES = "conference_invites\""""),
], skip_if_marker='const val CONFERENCE_SECRETARY_HOME')


# ============================================================
# 8. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    ("import com.pathfinder.hub.ui.screens.home.conference.ConferenceHomeScreen",
     "import com.pathfinder.hub.ui.screens.home.conference.ConferenceHomeScreen\n"
     "import com.pathfinder.hub.ui.screens.home.conference.ConferenceSecretaryHomeScreen\n"
     "import com.pathfinder.hub.ui.screens.home.conference.ConferenceClubsScreen\n"
     "import com.pathfinder.hub.ui.screens.home.conference.ConferenceMembersScreen\n"
     "import com.pathfinder.hub.ui.screens.home.conference.ConferenceInvitesScreen"),

    # заменяем composable(Routes.CONFERENCE_HOME)
    ("""        // === Conference ===
        composable(Routes.CONFERENCE_HOME) {
            ConferenceHomeScreen(
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }""",
     """        // === Conference ===
        composable(Routes.CONFERENCE_HOME) {
            ConferenceHomeScreen(
                onNavigateToClubs = { navController.navigate(Routes.CONFERENCE_CLUBS) },
                onNavigateToMembers = { navController.navigate(Routes.CONFERENCE_MEMBERS) },
                onNavigateToInvites = { navController.navigate(Routes.CONFERENCE_INVITES) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CONFERENCE_SECRETARY_HOME) {
            ConferenceSecretaryHomeScreen(
                onNavigateToClubs = { navController.navigate(Routes.CONFERENCE_CLUBS) },
                onNavigateToMembers = { navController.navigate(Routes.CONFERENCE_MEMBERS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CONFERENCE_CLUBS) {
            ConferenceClubsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CONFERENCE_MEMBERS) {
            ConferenceMembersScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CONFERENCE_INVITES) {
            ConferenceInvitesScreen(onBack = { navController.popBackStack() })
        }"""),
], skip_if_marker="Routes.CONFERENCE_SECRETARY_HOME")


# ============================================================
# 9. RoleHomeScreen.kt — маршрутизация conference_secretary
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/RoleHomeScreen.kt", [
    ("""                "conference_admin" -> Routes.CONFERENCE_HOME""",
     """                "conference_admin" -> Routes.CONFERENCE_HOME
                "conference_secretary" -> Routes.CONFERENCE_SECRETARY_HOME"""),
], skip_if_marker="conference_secretary")


# ============================================================
# 10. firestore.rules — чтение users для conference staff
# ============================================================
patch("firestore.rules", [
    ("""    match /users/{userId} {
      allow read: if isSignedIn() && (
        uid() == userId
        || isConferenceAdmin()
        || (isClubStaff() && userDoc().clubId == resource.data.clubId)
      );""",
     """    match /users/{userId} {
      allow read: if isSignedIn() && (
        uid() == userId
        || isConferenceAdmin()
        || (isConferenceSecretary() && resource.data.conferenceId == userConferenceId())
        || (isClubStaff() && userDoc().clubId == resource.data.clubId)
      );"""),
], skip_if_marker="isConferenceSecretary() && resource.data.conferenceId")


print()
print("=" * 60)
print("CS2 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("  firebase deploy --only firestore:rules")
print()
print("Что сделано:")
print("  - ConferenceStaffViewModel — общий VM (admin + secretary)")
print("  - ConferenceSecretaryHomeScreen — панель секретаря конференции")
print("  - ConferenceClubsScreen — список клубов конференции")
print("  - ConferenceMembersScreen — кадры конференции")
print("  - ConferenceInvitesScreen + VM — для conference_admin")
print("  - ConferenceHomeScreen — карточки Клубы, Кадры, Инвайты")
print("  - Routes + NavHost — 4 маршрута")
print("  - RoleHomeScreen — маршрутизация conference_secretary")
print("  - firestore.rules — чтение users для conference staff")
print()
print("Проверка:")
print("  1. Вход под conference_admin → карточки Клубы, Кадры, Инвайты")
print("  2. 'Инвайты' → 'Создать инвайт-код' → получить код")
print("  3. Второй человек регистрируется по коду → он conference_secretary")
print("  4. Входит → видит ConferenceSecretaryHomeScreen")
print("  5. Клубы и Кадры работают у обоих")
print()
print("Откат: .bak.<timestamp>")