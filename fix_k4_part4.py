#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
K4 part 4 (UI + permissions enforcement) - 09.10.2026

- NEW: PermissionChecker.kt
- PATCH: DirectorHomeViewModel — currentUser в state
- PATCH: DirectorHomeScreen — карточки с проверкой права, snackbar "Нет доступа", карточка "Сотрудники"
- NEW: StaffScreen + StaffViewModel — список director-ов клуба
- NEW: ManagePermissionsScreen + ManagePermissionsViewModel — чекбоксы прав
- PATCH: Routes + PathfinderNavHost

Запуск:
    python fix_k4_part4.py
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
# 1. PermissionChecker.kt
# ============================================================
CHECKER = '''package com.pathfinder.hub.domain.permissions

import com.pathfinder.hub.data.local.entity.core.UserEntity

/**
 * Хелпер проверки прав директора.
 *
 * Логика:
 *   - role != "director" → false
 *   - isOwnerClub = true → все права
 *   - иначе → perm in directorPermissions
 */
object PermissionChecker {

    fun hasPerm(user: UserEntity?, perm: String): Boolean {
        if (user == null) return false
        if (user.role != "director") return false
        if (user.isOwnerClub) return true
        return perm in user.directorPermissions
    }

    /** Публичный сахар: hasAny("a", "b", "c"). */
    fun hasAny(user: UserEntity?, vararg perms: String): Boolean =
        perms.any { hasPerm(user, it) }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/domain/permissions/PermissionChecker.kt", CHECKER)


# ============================================================
# 2. DirectorHomeViewModel — +currentUser
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeViewModel.kt", [
    # импорт UserEntity
    ("import com.pathfinder.hub.data.local.entity.core.ClubEntity",
     "import com.pathfinder.hub.data.local.entity.core.ClubEntity\nimport com.pathfinder.hub.data.local.entity.core.UserEntity"),

    # state + currentUser
    ("""    val clubs: List<ClubEntity> = emptyList(),
    val selectedClubId: String? = null,
)""",
     """    val clubs: List<ClubEntity> = emptyList(),
    val selectedClubId: String? = null,
    val currentUser: UserEntity? = null,
)"""),

    # сохраняем юзера в state
    ("""                _state.update {
                    it.copy(
                        isLoading = false,
                        directorName = user.firstName,
                        clubName = active?.name,
                        clubLocation = active?.city,
                        clubs = clubs,
                        selectedClubId = activeId,
                    )
                }""",
     """                _state.update {
                    it.copy(
                        isLoading = false,
                        directorName = user.firstName,
                        clubName = active?.name,
                        clubLocation = active?.city,
                        clubs = clubs,
                        selectedClubId = activeId,
                        currentUser = user,
                    )
                }"""),
], skip_if_marker="currentUser: UserEntity?")


# ============================================================
# 3. DirectorHomeScreen — REWRITE
# ============================================================
DIR_HOME = '''package com.pathfinder.hub.ui.screens.home.director

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
import com.pathfinder.hub.domain.permissions.PermissionChecker
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
    onNavigateToStaff: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
    viewModel: DirectorHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLogout()
    }

    /**
     * Обёртка: если права нет — snackbar «Нет доступа», иначе — навигация.
     */
    fun guard(perm: String, onAllow: () -> Unit) {
        if (PermissionChecker.hasPerm(state.currentUser, perm)) {
            onAllow()
        } else {
            // Показываем сообщение через coroutine
            kotlinx.coroutines.MainScope().launch {
                snackbarHostState.showSnackbar("Нет доступа. Обратитесь к владельцу клуба.")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

                val u = state.currentUser

                // ---------- Ряд 1: Инвайты + Участники ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        icon = Icons.Default.PersonAdd,
                        title = "Инвайты",
                        description = "Создать и управлять кодами",
                        perm = "invites.create",
                        user = u,
                        onClick = { guard("invites.create") { onNavigateToInvites() } },
                        modifier = Modifier.weight(1f),
                    )
                    GuardedCard(
                        icon = Icons.Default.Group,
                        title = "Участники",
                        description = "Список членов клуба",
                        perm = "members.view",
                        user = u,
                        onClick = { guard("members.view") { onNavigateToMembers() } },
                        modifier = Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 2: Одобрение + Отчёты ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        icon = Icons.Default.CheckCircle,
                        title = "Одобрение",
                        description = "Проверка требований",
                        perm = "learning.approve",
                        user = u,
                        onClick = { guard("learning.approve") { onNavigateToApprovals() } },
                        modifier = Modifier.weight(1f),
                    )
                    GuardedCard(
                        icon = Icons.Default.Assessment,
                        title = "Отчёты",
                        description = "Генерация и история",
                        perm = "reports.view",
                        user = u,
                        onClick = { guard("reports.view") { onNavigateToReports() } },
                        modifier = Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 3: События + Настройки ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        icon = Icons.Default.Event,
                        title = "События",
                        description = "Расписание клуба",
                        perm = "events.create",
                        user = u,
                        onClick = { guard("events.create") { onNavigateToEvents() } },
                        modifier = Modifier.weight(1f),
                    )
                    GuardedCard(
                        icon = Icons.Default.Settings,
                        title = "Настройки",
                        description = "Параметры клуба",
                        perm = "settings.view",
                        user = u,
                        onClick = { guard("settings.view") { onNavigateToSettings() } },
                        modifier = Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 4: Задания + Чат клуба (чат — всем) ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        icon = Icons.Default.TaskAlt,
                        title = "Задания",
                        description = "Назначить следопытам",
                        perm = "tasks.create",
                        user = u,
                        onClick = { guard("tasks.create") { onNavigateToTasks() } },
                        modifier = Modifier.weight(1f),
                    )
                    DirectorActionCard(
                        Icons.Default.Chat, "Чат клуба", "Общее обсуждение",
                        onNavigateToClubChat, Modifier.weight(1f)
                    )
                }

                // ---------- Ряд 5: Модерация + Контент ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        icon = Icons.Default.Shield,
                        title = "Модерация",
                        description = "Проверка сообщений",
                        perm = "moderation.review",
                        user = u,
                        onClick = { guard("moderation.review") { onNavigateToModeration() } },
                        modifier = Modifier.weight(1f),
                    )
                    GuardedCard(
                        icon = Icons.Default.Edit,
                        title = "Контент",
                        description = "Ступени и требования",
                        perm = "content.edit",
                        user = u,
                        onClick = { guard("content.edit") { onNavigateToContentManager() } },
                        modifier = Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 6: Чаты (всем) + QR-станции ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DirectorActionCard(
                        Icons.Default.Email, "Чаты", "Личные и группы",
                        onNavigateToChats, Modifier.weight(1f)
                    )
                    GuardedCard(
                        icon = Icons.Default.QrCode,
                        title = "QR-станции",
                        description = "Точки для сканирования",
                        perm = "qr.create",
                        user = u,
                        onClick = { guard("qr.create") { onNavigateToQrStations() } },
                        modifier = Modifier.weight(1f),
                    )
                }

                // ---------- Сотрудники (только owner) ----------
                if (u?.isOwnerClub == true) {
                    Card(
                        onClick = onNavigateToStaff,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ManageAccounts, null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Сотрудники",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "Управление правами со-директоров",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
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

/**
 * Карточка с проверкой права. Если права нет — серая (alpha 0.45),
 * но нажимается (для snackbar «Нет доступа»).
 */
@Composable
private fun GuardedCard(
    icon: ImageVector,
    title: String,
    description: String,
    perm: String,
    user: com.pathfinder.hub.data.local.entity.core.UserEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allowed = PermissionChecker.hasPerm(user, perm)
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .then(if (allowed) Modifier else Modifier.alphaCompat(0.45f)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, contentDescription = null,
                tint = if (allowed) PathfinderBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (allowed) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
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

/**
 * Локальная альтернатива Modifier.alpha — не требуем импорт
 * androidx.compose.ui.draw.alpha (чтобы не менять build.gradle).
 */
private fun Modifier.alphaCompat(value: Float): Modifier = this.then(
    androidx.compose.ui.draw.alpha(value)
)
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeScreen.kt", DIR_HOME)


# ============================================================
# 4. StaffViewModel.kt + StaffScreen.kt
# ============================================================
STAFF_VM = '''package com.pathfinder.hub.ui.screens.home.director.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaffUiState(
    val isLoading: Boolean = true,
    val directors: List<UserEntity> = emptyList(),
    val currentUserId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StaffUiState())
    val state: StateFlow<StaffUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            _state.update { it.copy(currentUserId = uid) }
            load(uid)
        }
    }

    private fun load(userId: String) {
        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            val clubId = user?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            userRepository.observeClubMembersByRole(clubId, "director").collect { list ->
                _state.update { it.copy(isLoading = false, directors = list) }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/staff/StaffViewModel.kt", STAFF_VM)


STAFF_SCREEN = '''package com.pathfinder.hub.ui.screens.home.director.staff

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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
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
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    onBack: () -> Unit,
    onOpenPermissions: (String) -> Unit,
    viewModel: StaffViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сотрудники") },
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
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.directors.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Person, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Нет директоров", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.directors, key = { it.id }) { d ->
                    DirectorRow(
                        user = d,
                        isMe = d.id == state.currentUserId,
                        onClick = { if (!d.isOwnerClub) onOpenPermissions(d.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectorRow(
    user: UserEntity,
    isMe: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (user.isOwnerClub) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                null,
                tint = PathfinderBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${user.firstName} ${user.lastName}".trim() + if (isMe) " (вы)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (user.isOwnerClub) "Владелец клуба" else "Со-директор · ${user.directorPermissions.size} прав",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(if (user.isOwnerClub) "Owner" else "Co-dir") }
            )
            if (!user.isOwnerClub) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/staff/StaffScreen.kt", STAFF_SCREEN)


# ============================================================
# 5. ManagePermissions VM + Screen
# ============================================================
MANAGE_VM = '''package com.pathfinder.hub.ui.screens.home.director.staff

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManagePermissionsUiState(
    val isLoading: Boolean = true,
    val target: UserEntity? = null,
    val selected: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedMessage: String? = null,
)

@HiltViewModel
class ManagePermissionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val targetId: String = savedStateHandle.get<String>("userId") ?: ""

    private val _state = MutableStateFlow(ManagePermissionsUiState())
    val state: StateFlow<ManagePermissionsUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val myId = sessionManager.getUserId()
        if (myId == null || targetId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Неверный запрос") }
            return
        }
        viewModelScope.launch {
            val me = userRepository.getUser(myId)
            if (me == null || !me.isOwnerClub) {
                _state.update { it.copy(isLoading = false, errorMessage = "Только владелец клуба может менять права") }
                return@launch
            }
            userRepository.observeUser(targetId).collect { user ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        target = user,
                        selected = user?.directorPermissions?.toSet() ?: emptySet(),
                    )
                }
            }
        }
    }

    fun toggle(perm: String) {
        _state.update {
            val cur = it.selected
            val next = if (perm in cur) cur - perm else cur + perm
            it.copy(selected = next, savedMessage = null)
        }
    }

    fun save() {
        val uid = sessionManager.getUserId() ?: return
        val target = _state.value.target ?: return
        if (target.isOwnerClub) {
            _state.update { it.copy(errorMessage = "Владелец клуба всегда имеет полные права") }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null, savedMessage = null) }
        viewModelScope.launch {
            try {
                userRepository.update(
                    target.copy(directorPermissions = _state.value.selected.toList())
                )
                _state.update { it.copy(isSaving = false, savedMessage = "Сохранено") }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка сохранения") }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(savedMessage = null, errorMessage = null) }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/staff/ManagePermissionsViewModel.kt", MANAGE_VM)


MANAGE_SCREEN = '''package com.pathfinder.hub.ui.screens.home.director.staff

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.pathfinder.hub.domain.permissions.PermissionRegistry
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePermissionsScreen(
    onBack: () -> Unit,
    viewModel: ManagePermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text("Права: " + (state.target?.let { "${it.firstName} ${it.lastName}".trim() } ?: ""))
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
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.target == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Пользователь не найден")
            }
            state.target?.isOwnerClub == true -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Text("Это владелец клуба. Владелец всегда имеет полные права.")
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "Отметьте, что может делать со-директор",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Владелец клуба всегда имеет все права.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                PermissionRegistry.GROUPS.forEach { group ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                group.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            group.permissions.forEach { perm ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = perm.key in state.selected,
                                        onCheckedChange = { viewModel.toggle(perm.key) }
                                    )
                                    Text(perm.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/staff/ManagePermissionsScreen.kt", MANAGE_SCREEN)


# ============================================================
# 6. Routes
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    const val CREATE_CLUB = "create_club\"""",
     """    const val CREATE_CLUB = "create_club"
    const val STAFF = "staff"
    const val MANAGE_PERMISSIONS = "manage_permissions/{userId}"
    fun managePermissions(userId: String) = "manage_permissions/$userId\""""),
], skip_if_marker='const val STAFF')


# ============================================================
# 7. PathfinderNavHost
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    ("import com.pathfinder.hub.ui.screens.clubs.CreateClubScreen",
     "import com.pathfinder.hub.ui.screens.clubs.CreateClubScreen\n"
     "import com.pathfinder.hub.ui.screens.home.director.staff.StaffScreen\n"
     "import com.pathfinder.hub.ui.screens.home.director.staff.ManagePermissionsScreen"),

    # колбэк в DirectorHome
    ("""                onNavigateToCreateClub = { navController.navigate(Routes.CREATE_CLUB) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },""",
     """                onNavigateToCreateClub = { navController.navigate(Routes.CREATE_CLUB) },
                onNavigateToStaff = { navController.navigate(Routes.STAFF) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },"""),

    # composable после CREATE_CLUB
    ("""        composable(Routes.CREATE_CLUB) {
            CreateClubScreen(
                onBack = { navController.popBackStack() },
                onCreated = {
                    // После создания возвращаемся на главную директора
                    // — вкладки клубов уже включат новый клуб.
                    navController.popBackStack()
                }
            )
        }""",
     """        composable(Routes.CREATE_CLUB) {
            CreateClubScreen(
                onBack = { navController.popBackStack() },
                onCreated = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.STAFF) {
            StaffScreen(
                onBack = { navController.popBackStack() },
                onOpenPermissions = { userId ->
                    navController.navigate(Routes.managePermissions(userId))
                }
            )
        }

        composable(
            route = Routes.MANAGE_PERMISSIONS,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            ManagePermissionsScreen(onBack = { navController.popBackStack() })
        }"""),
], skip_if_marker="Routes.MANAGE_PERMISSIONS")


print()
print("=" * 60)
print("K4 part 4 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Что сделано:")
print("  - PermissionChecker: hasPerm(user, perm)")
print("  - DirectorHomeViewModel: currentUser в state")
print("  - DirectorHomeScreen: карточки серые при отсутствии права,")
print("      snackbar 'Нет доступа', карточка 'Сотрудники' (только owner)")
print("  - StaffScreen/VM: список director-ов клуба")
print("  - ManagePermissionsScreen/VM: чекбоксы прав + Сохранить")
print("  - Routes: STAFF, MANAGE_PERMISSIONS/{userId}")
print()
print("Проверка:")
print("  1. Владелец → Главная → все карточки цветные + 'Сотрудники'")
print("  2. Со-директор с урезанными правами → некоторые карточки серые")
print("  3. Тап на серую → snackbar 'Нет доступа'")
print("  4. Владелец → 'Сотрудники' → со-директор → чекбоксы → Сохранить")
print("  5. Со-директор заходит → видит применённые права")
print()
print("Откат: .bak.<timestamp>")