#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Multi-club K2 (tabs) - 09.10.2026

- DirectorHomeViewModel: список клубов + selectedClubId + switchClub()
- DirectorHomeScreen: TabRow при >=2 клубах, иначе без вкладок
- Остальные экраны директора работают через user.clubId (= активный)

Запуск:
    python fix_multi_club_k2.py
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
# 1. DirectorHomeViewModel.kt
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.ClubEntity
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

data class DirectorHomeUiState(
    val isLoading: Boolean = true,
    val directorName: String? = null,
    val clubName: String? = null,
    val clubLocation: String? = null,
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
    val clubs: List<ClubEntity> = emptyList(),
    val selectedClubId: String? = null,
)

@HiltViewModel
class DirectorHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorHomeUiState())
    val state: StateFlow<DirectorHomeUiState> = _state.asStateFlow()

    init {
        observeDirector()
    }

    /**
     * Подписка на user — важно, чтобы при смене активного клуба
     * UI сразу перерисовывал заголовок и вкладки.
     */
    private fun observeDirector() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            userRepository.observeUser(userId).collect { user ->
                if (user == null) return@collect

                // Список клубов: clubIds, fallback [clubId]
                val ids = user.clubIds.ifEmpty {
                    listOfNotNull(user.clubId)
                }
                val activeId = user.clubId ?: ids.firstOrNull()

                // Загружаем сущности клубов
                val clubs = ids.mapNotNull { id ->
                    try { clubRepository.getClub(id) } catch (_: Exception) { null }
                }

                val active = clubs.find { it.id == activeId }

                _state.update {
                    it.copy(
                        isLoading = false,
                        directorName = user.firstName,
                        clubName = active?.name,
                        clubLocation = active?.city,
                        clubs = clubs,
                        selectedClubId = activeId,
                    )
                }
            }
        }
    }

    /**
     * Смена активного клуба. Обновляет user.clubId, который читают
     * все экраны директора.
     */
    fun switchClub(clubId: String) {
        val userId = sessionManager.getUserId() ?: return
        if (_state.value.selectedClubId == clubId) return
        viewModelScope.launch {
            try {
                userRepository.setActiveClub(userId, clubId)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
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
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeViewModel.kt", VM)


# ============================================================
# 2. DirectorHomeScreen.kt
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.screens.home.director

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun DirectorHomeScreen(
    onNavigateToInvites: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToApprovals: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToEvents: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToClubChat: () -> Unit,
    onNavigateToModeration: () -> Unit,
    onNavigateToContentManager: () -> Unit = {},
    onNavigateToChats: () -> Unit = {},
    onNavigateToQrStations: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
    viewModel: DirectorHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLogout()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Панель директора") },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Уведомления",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Выйти",
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
        ) {
            // ---------- Вкладки клубов (только если > 1) ----------
            if (state.clubs.size > 1) {
                val selectedIndex = state.clubs
                    .indexOfFirst { it.id == state.selectedClubId }
                    .coerceAtLeast(0)

                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    state.clubs.forEach { club ->
                        Tab(
                            selected = club.id == state.selectedClubId,
                            onClick = { viewModel.switchClub(club.id) },
                            text = {
                                Text(
                                    club.name,
                                    maxLines = 1,
                                    fontWeight = if (club.id == state.selectedClubId)
                                        FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // ---------- Контент ----------
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Добро пожаловать, ${state.directorName ?: "Директор"}!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Активный клуб",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.clubName ?: "Загрузка...",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.clubLocation ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Text(
                    "Управление клубом",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DirectorActionCard(Icons.Default.PersonAdd, "Инвайты", "Создать и управлять кодами", onNavigateToInvites, Modifier.weight(1f))
                    DirectorActionCard(Icons.Default.Group, "Участники", "Список членов клуба", onNavigateToMembers, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DirectorActionCard(Icons.Default.CheckCircle, "Одобрение", "Проверка требований", onNavigateToApprovals, Modifier.weight(1f))
                    DirectorActionCard(Icons.Default.Assessment, "Отчёты", "Генерация и история", onNavigateToReports, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DirectorActionCard(Icons.Default.Event, "События", "Расписание клуба", onNavigateToEvents, Modifier.weight(1f))
                    DirectorActionCard(Icons.Default.Settings, "Настройки", "Параметры клуба", onNavigateToSettings, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DirectorActionCard(Icons.Default.TaskAlt, "Задания", "Назначить следопытам", onNavigateToTasks, Modifier.weight(1f))
                    DirectorActionCard(Icons.Default.Chat, "Чат клуба", "Общее обсуждение", onNavigateToClubChat, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DirectorActionCard(Icons.Default.Shield, "Модерация", "Проверка сообщений", onNavigateToModeration, Modifier.weight(1f))
                    DirectorActionCard(Icons.Default.Edit, "Контент", "Ступени и требования", onNavigateToContentManager, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DirectorActionCard(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
                    DirectorActionCard(Icons.Default.QrCode, "QR-станции", "Точки для сканирования", onNavigateToQrStations, Modifier.weight(1f))
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

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Вы уверены, что хотите выйти? Для повторного входа потребуется email и пароль.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun DirectorActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = PathfinderBlue, modifier = Modifier.size(32.dp))
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
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeScreen.kt", SCREEN)


print()
print("=" * 60)
print("K2 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Что сделано:")
print("  - DirectorHomeViewModel: подписка на user, список clubs, switchClub()")
print("  - DirectorHomeScreen: ScrollableTabRow при >=2 клубах")
print()
print("Проверка (нужен юзер с 2+ клубами):")
print("  1. Директор → Главная → сверху вкладки клубов")
print("  2. Переключение → меняется 'Активный клуб' и все экраны ниже")
print("  3. Участники, События, Инвайты — все для активного клуба")
print()
print("Откат: .bak.<timestamp>")