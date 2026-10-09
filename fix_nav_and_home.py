#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Перезапись DirectorHomeScreen + PathfinderNavHost — 09.10.2026
Запуск:
    python fix_nav_and_home.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def rewrite(path, content):
    p = ROOT / path
    if not p.exists():
        print("[ERROR] не найден: " + path); sys.exit(1)
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[REWRITE] " + path)


# ============================================================
# 1. DirectorHomeScreen.kt
# ============================================================
HOME = '''package com.pathfinder.hub.ui.screens.home.director

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
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
    onNavigateToQrQuests: () -> Unit = {},
    onNavigateToHomework: () -> Unit = {},
    onNavigateToReviewHomework: () -> Unit = {},
    onNavigateToPayments: () -> Unit = {},
    onNavigateToStaff: () -> Unit = {},
    onNavigateToCreateClub: () -> Unit = {},
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

    val scope = rememberCoroutineScope()
    fun guard(perm: String, onAllow: () -> Unit) {
        if (PermissionChecker.hasPerm(state.currentUser, perm)) {
            onAllow()
        } else {
            scope.launch {
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
            modifier = Modifier.fillMaxSize().padding(padding)
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
                        Icons.Default.PersonAdd, "Инвайты", "Создать и управлять кодами",
                        "invites.create", u,
                        { guard("invites.create") { onNavigateToInvites() } },
                        Modifier.weight(1f),
                    )
                    GuardedCard(
                        Icons.Default.Group, "Участники", "Список членов клуба",
                        "members.view", u,
                        { guard("members.view") { onNavigateToMembers() } },
                        Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 2: Одобрение + Отчёты ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        Icons.Default.CheckCircle, "Одобрение", "Проверка требований",
                        "learning.approve", u,
                        { guard("learning.approve") { onNavigateToApprovals() } },
                        Modifier.weight(1f),
                    )
                    GuardedCard(
                        Icons.Default.Assessment, "Отчёты", "Генерация и история",
                        "reports.view", u,
                        { guard("reports.view") { onNavigateToReports() } },
                        Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 3: События + Настройки ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        Icons.Default.Event, "События", "Расписание клуба",
                        "events.create", u,
                        { guard("events.create") { onNavigateToEvents() } },
                        Modifier.weight(1f),
                    )
                    GuardedCard(
                        Icons.Default.Settings, "Настройки", "Параметры клуба",
                        "settings.view", u,
                        { guard("settings.view") { onNavigateToSettings() } },
                        Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 4: Задания + Чат клуба ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        Icons.Default.TaskAlt, "Задания", "Назначить следопытам",
                        "tasks.create", u,
                        { guard("tasks.create") { onNavigateToTasks() } },
                        Modifier.weight(1f),
                    )
                    DirectorActionCard(
                        Icons.Default.Chat, "Чат клуба", "Общее обсуждение",
                        onNavigateToClubChat, Modifier.weight(1f)
                    )
                }

                // ---------- Ряд 5: Модерация + Контент ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        Icons.Default.Shield, "Модерация", "Проверка сообщений",
                        "moderation.review", u,
                        { guard("moderation.review") { onNavigateToModeration() } },
                        Modifier.weight(1f),
                    )
                    GuardedCard(
                        Icons.Default.Edit, "Контент", "Ступени и требования",
                        "content.edit", u,
                        { guard("content.edit") { onNavigateToContentManager() } },
                        Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 6: Чаты + QR-станции ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DirectorActionCard(
                        Icons.Default.Email, "Чаты", "Личные и группы",
                        onNavigateToChats, Modifier.weight(1f)
                    )
                    GuardedCard(
                        Icons.Default.QrCode, "QR-станции", "Точки для сканирования",
                        "qr.create", u,
                        { guard("qr.create") { onNavigateToQrStations() } },
                        Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 7: Домашка + Проверка ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        Icons.Default.Assignment, "Домашка", "Назначить следопытам",
                        "learning.homework", u,
                        { guard("learning.homework") { onNavigateToHomework() } },
                        Modifier.weight(1f),
                    )
                    GuardedCard(
                        Icons.Default.FactCheck, "Проверка", "Сданные домашки",
                        "learning.homework", u,
                        { guard("learning.homework") { onNavigateToReviewHomework() } },
                        Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 8: QR-квесты + Взносы ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        Icons.Default.Route, "QR-квесты", "Серии станций",
                        "qr.create", u,
                        { guard("qr.create") { onNavigateToQrQuests() } },
                        Modifier.weight(1f),
                    )
                    GuardedCard(
                        Icons.Default.AccountBalance, "Взносы", "Оплата от родителей",
                        "finance.view", u,
                        { guard("finance.view") { onNavigateToPayments() } },
                        Modifier.weight(1f),
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

                // ---------- Создать ещё один клуб ----------
                Card(
                    onClick = onNavigateToCreateClub,
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
                            Icons.Default.AddBusiness, null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Создать ещё один клуб",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Станете директором нового клуба",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
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
                .then(if (allowed) Modifier else Modifier.alpha(0.45f)),
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
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeScreen.kt", HOME)


# ============================================================
# 2. PathfinderNavHost.kt
# ============================================================
NAV = '''package com.pathfinder.hub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pathfinder.hub.MainActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pathfinder.hub.ui.challenges.ChallengeDetailScreen
import com.pathfinder.hub.ui.challenges.ChallengesScreen
import com.pathfinder.hub.ui.chat.list.ChatsListScreen
import com.pathfinder.hub.ui.chat.dm.DMChatScreen
import com.pathfinder.hub.ui.chat.group.CreateGroupChatScreen
import com.pathfinder.hub.ui.chat.group.CustomGroupChatScreen
import com.pathfinder.hub.ui.chat.ClubChatScreen
import com.pathfinder.hub.ui.chat.GroupChatScreen
import com.pathfinder.hub.ui.gamification.AchievementsScreen
import com.pathfinder.hub.ui.gamification.DigitalUniformScreen
import com.pathfinder.hub.ui.moderation.ModerationScreen
import com.pathfinder.hub.ui.notifications.NotificationsScreen
import com.pathfinder.hub.ui.activity.ActivityScreen
import com.pathfinder.hub.ui.qr.QrScannerScreen
import com.pathfinder.hub.ui.qr.ManageStationsScreen
import com.pathfinder.hub.ui.qr.CreateStationScreen
import com.pathfinder.hub.ui.qr.quests.ManageQuestsScreen
import com.pathfinder.hub.ui.qr.quests.QuestEditorScreen
import com.pathfinder.hub.ui.reports.CreateReportScreen
import com.pathfinder.hub.ui.reports.ReportDetailScreen
import com.pathfinder.hub.ui.reports.ReportHistoryScreen
import com.pathfinder.hub.ui.screens.auth.LoginScreen
import com.pathfinder.hub.ui.screens.auth.RegisterScreen
import com.pathfinder.hub.ui.screens.events.CreateEventScreen
import com.pathfinder.hub.ui.screens.events.EventDetailScreen
import com.pathfinder.hub.ui.screens.events.EventsScreen
import com.pathfinder.hub.ui.screens.home.RoleHomeScreen
import com.pathfinder.hub.ui.screens.home.director.ApprovalsScreen
import com.pathfinder.hub.ui.screens.home.director.ClubMembersScreen
import com.pathfinder.hub.ui.screens.home.director.ClubSettingsScreen
import com.pathfinder.hub.ui.screens.home.director.DirectorHomeScreen
import com.pathfinder.hub.ui.screens.home.director.DirectorInvitesScreen
import com.pathfinder.hub.ui.screens.home.director.staff.StaffScreen
import com.pathfinder.hub.ui.screens.home.director.staff.ManagePermissionsScreen
import com.pathfinder.hub.ui.screens.home.teen.TeenHomeScreen
import com.pathfinder.hub.ui.screens.home.instructor.InstructorHomeScreen
import com.pathfinder.hub.ui.screens.home.secretary.SecretaryHomeScreen
import com.pathfinder.hub.ui.screens.home.parent.ParentHomeScreen
import com.pathfinder.hub.ui.screens.home.parent.ChildProgressScreen
import com.pathfinder.hub.ui.screens.home.parent.ParentPaymentsScreen
import com.pathfinder.hub.ui.screens.home.parent.ParentMessagesScreen
import com.pathfinder.hub.ui.screens.home.conference.ConferenceHomeScreen
import com.pathfinder.hub.ui.screens.home.conference.ConferenceSecretaryHomeScreen
import com.pathfinder.hub.ui.screens.home.conference.ConferenceClubsScreen
import com.pathfinder.hub.ui.screens.home.conference.ConferenceMembersScreen
import com.pathfinder.hub.ui.screens.home.conference.ConferenceInvitesScreen
import com.pathfinder.hub.ui.screens.homework.CreateHomeworkScreen
import com.pathfinder.hub.ui.screens.homework.review.ReviewHomeworkScreen
import com.pathfinder.hub.ui.screens.clubs.CreateClubScreen
import com.pathfinder.hub.ui.screens.payments.DirectorPaymentsScreen
import com.pathfinder.hub.ui.screens.payments.CreatePaymentScreen
import com.pathfinder.hub.ui.screens.certificates.MyCertificatesScreen
import com.pathfinder.hub.ui.screens.honors.HonorCatalogScreen
import com.pathfinder.hub.ui.screens.honors.HonorDetailScreen
import com.pathfinder.hub.ui.screens.honors.HonorRequirementDetailScreen
import com.pathfinder.hub.ui.screens.honors.MyHonorsScreen
import com.pathfinder.hub.ui.screens.honors.TestScreen
import com.pathfinder.hub.ui.screens.levels.LevelDetailScreen
import com.pathfinder.hub.ui.screens.levels.MyLevelsScreen
import com.pathfinder.hub.ui.screens.levels.RequirementDetailScreen
import com.pathfinder.hub.ui.learning.content.ContentManagerScreen
import com.pathfinder.hub.ui.learning.content.ContentLevelScreen
import com.pathfinder.hub.ui.learning.content.ContentSectionScreen
import com.pathfinder.hub.ui.learning.editor.RequirementEditorScreen
import com.pathfinder.hub.ui.screens.onboarding.OnboardingScreen
import com.pathfinder.hub.ui.screens.profile.TeenProfileScreen
import com.pathfinder.hub.ui.screens.settings.SettingsScreen
import com.pathfinder.hub.ui.screens.sync.SyncScreen
import com.pathfinder.hub.ui.screens.tasks.CreateTaskScreen
import com.pathfinder.hub.ui.screens.tasks.MyTasksScreen
import com.pathfinder.hub.ui.storage.FileUploadResult
import com.pathfinder.hub.ui.storage.FileUploadScreen

@Composable
fun PathfinderNavHost(
    navController: NavHostController,
    startDestination: String = Routes.LOGIN
) {
    LaunchedEffect(Unit) {
        MainActivity.deepLinks.collect { link ->
            when (link.type) {
                "dm" -> {
                    val other = link.otherUid ?: ""
                    navController.navigate(Routes.dmChat(link.targetId, other))
                }
                "group" -> navController.navigate(Routes.customGroupChat(link.targetId))
                "club_chat" -> navController.navigate(Routes.CLUB_CHAT)
                "chats_list" -> navController.navigate(Routes.CHATS_LIST)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ===== Auth =====
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.ROLE_HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToInvite = { navController.navigate(Routes.REGISTER) }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = { role, userId ->
                    navController.navigate(Routes.onboarding(role, userId)) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.ONBOARDING,
            arguments = listOf(
                navArgument("role") { type = NavType.StringType; defaultValue = "teen" },
                navArgument("userId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "teen"
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            OnboardingScreen(
                role = role,
                userId = userId,
                onFinish = {
                    navController.navigate(Routes.ROLE_HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ROLE_HOME) {
            RoleHomeScreen(navController = navController)
        }

        // ===== Instructor =====
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
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }

        // ===== Secretary =====
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
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }

        // ===== Parent =====
        composable(Routes.PARENT_HOME) {
            ParentHomeScreen(
                onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenChild = { childId -> navController.navigate(Routes.childProgress(childId)) },
                onNavigateToPayments = { navController.navigate(Routes.PARENT_PAYMENTS) },
                onNavigateToMessages = { navController.navigate(Routes.PARENT_MESSAGES) },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(
            route = Routes.CHILD_PROGRESS,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) {
            ChildProgressScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PARENT_PAYMENTS) {
            ParentPaymentsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PARENT_MESSAGES) {
            ParentMessagesScreen(onBack = { navController.popBackStack() })
        }

        // ===== Conference =====
        composable(Routes.CONFERENCE_HOME) {
            ConferenceHomeScreen(
                onNavigateToClubs = { navController.navigate(Routes.CONFERENCE_CLUBS) },
                onNavigateToMembers = { navController.navigate(Routes.CONFERENCE_MEMBERS) },
                onNavigateToInvites = { navController.navigate(Routes.CONFERENCE_INVITES) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(Routes.CONFERENCE_SECRETARY_HOME) {
            ConferenceSecretaryHomeScreen(
                onNavigateToClubs = { navController.navigate(Routes.CONFERENCE_CLUBS) },
                onNavigateToMembers = { navController.navigate(Routes.CONFERENCE_MEMBERS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
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
        }

        // ===== Director =====
        composable(Routes.DIRECTOR_HOME) {
            DirectorHomeScreen(
                onNavigateToInvites = { navController.navigate(Routes.DIRECTOR_INVITES) },
                onNavigateToMembers = { navController.navigate(Routes.CLUB_MEMBERS) },
                onNavigateToApprovals = { navController.navigate(Routes.APPROVALS) },
                onNavigateToReports = { navController.navigate(Routes.DIRECTOR_REPORTS) },
                onNavigateToEvents = { navController.navigate(Routes.CLUB_EVENTS) },
                onNavigateToSettings = { navController.navigate(Routes.CLUB_SETTINGS) },
                onNavigateToTasks = { navController.navigate(Routes.CREATE_TASK) },
                onNavigateToClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onNavigateToModeration = { navController.navigate(Routes.MODERATION) },
                onNavigateToContentManager = { navController.navigate(Routes.CONTENT_MANAGER) },
                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToQrStations = { navController.navigate(Routes.MY_STATIONS) },
                onNavigateToQrQuests = { navController.navigate(Routes.QUEST_MANAGE) },
                onNavigateToHomework = { navController.navigate(Routes.CREATE_HOMEWORK) },
                onNavigateToReviewHomework = { navController.navigate(Routes.REVIEW_HOMEWORK) },
                onNavigateToPayments = { navController.navigate(Routes.DIRECTOR_PAYMENTS) },
                onNavigateToStaff = { navController.navigate(Routes.STAFF) },
                onNavigateToCreateClub = { navController.navigate(Routes.CREATE_CLUB) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(Routes.DIRECTOR_INVITES) { DirectorInvitesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CLUB_MEMBERS) {
            ClubMembersScreen(
                onBack = { navController.popBackStack() },
                onOpenDM = { dmId, otherUid -> navController.navigate(Routes.dmChat(dmId, otherUid)) },
            )
        }
        composable(Routes.APPROVALS) { ApprovalsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.DIRECTOR_REPORTS) {
            ReportHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onReportClick = { reportId -> navController.navigate(Routes.reportDetail(reportId)) },
                onCreateReport = { navController.navigate(Routes.CREATE_REPORT) }
            )
        }
        composable(Routes.CREATE_REPORT) {
            CreateReportScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.CLUB_EVENTS) {
            EventsScreen(
                onBack = { navController.popBackStack() },
                onOpenEvent = { eventId -> navController.navigate(Routes.eventDetail(eventId)) },
                onCreateEvent = { navController.navigate(Routes.CREATE_EVENT) }
            )
        }
        composable(Routes.CREATE_EVENT) {
            CreateEventScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.CLUB_SETTINGS) { ClubSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CREATE_TASK) {
            CreateTaskScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.MY_STATIONS) {
            ManageStationsScreen(
                onBack = { navController.popBackStack() },
                onCreateStation = { navController.navigate(Routes.CREATE_STATION) }
            )
        }
        composable(Routes.CREATE_STATION) {
            CreateStationScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.STAFF) {
            StaffScreen(
                onBack = { navController.popBackStack() },
                onOpenPermissions = { userId -> navController.navigate(Routes.managePermissions(userId)) }
            )
        }
        composable(
            route = Routes.MANAGE_PERMISSIONS,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            ManagePermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.QUEST_MANAGE) {
            ManageQuestsScreen(
                onBack = { navController.popBackStack() },
                onOpenQuest = { questId -> navController.navigate(Routes.questEditor(questId)) },
                onCreateQuest = { navController.navigate(Routes.questEditor("new")) }
            )
        }
        composable(
            route = Routes.QUEST_EDITOR,
            arguments = listOf(navArgument("questId") { type = NavType.StringType })
        ) {
            QuestEditorScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
        }
        composable(Routes.CREATE_HOMEWORK) {
            CreateHomeworkScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.REVIEW_HOMEWORK) { ReviewHomeworkScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CREATE_CLUB) {
            CreateClubScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.DIRECTOR_PAYMENTS) {
            DirectorPaymentsScreen(
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate(Routes.CREATE_PAYMENT) }
            )
        }
        composable(Routes.CREATE_PAYMENT) {
            CreatePaymentScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }

        // ===== Teen Home =====
        composable(Routes.TEEN_HOME) {
            TeenHomeScreen(
                onNavigateToLevels = { navController.navigate(Routes.MY_LEVELS) },
                onNavigateToHonors = { navController.navigate(Routes.HONOR_CATALOG) },
                onNavigateToMyHonors = { navController.navigate(Routes.MY_HONORS) },
                onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                onNavigateToTasks = { navController.navigate(Routes.MY_TASKS) },
                onNavigateToProfile = { navController.navigate(Routes.TEEN_PROFILE) },
                onNavigateToDigitalUniform = { navController.navigate(Routes.DIGITAL_UNIFORM) },
                onNavigateToAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                onNavigateToChallenges = { navController.navigate(Routes.CHALLENGES) },
                onNavigateToReports = { navController.navigate(Routes.REPORT_HISTORY) },
                onNavigateToClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToCertificates = { navController.navigate(Routes.MY_CERTIFICATES) },
                onNavigateToQrScanner = { navController.navigate(Routes.QR_SCANNER) },
                onNavigateToActivity = { navController.navigate(Routes.ACTIVITY) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        }
        composable(Routes.MY_CERTIFICATES) { MyCertificatesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.QR_SCANNER) { QrScannerScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ACTIVITY) { ActivityScreen(onBack = { navController.popBackStack() }) }

        // ===== Teen Levels =====
        composable(Routes.MY_LEVELS) {
            MyLevelsScreen(
                onBack = { navController.popBackStack() },
                onOpenLevel = { levelId -> navController.navigate(Routes.levelDetail(levelId)) }
            )
        }
        composable(
            route = Routes.LEVEL_DETAIL,
            arguments = listOf(navArgument("levelId") { type = NavType.StringType })
        ) { backStackEntry ->
            LevelDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { lvlId, requirementId ->
                    navController.navigate(Routes.requirementDetail(lvlId, requirementId))
                },
                onUploadReport = { lvlId, requirementId ->
                    navController.navigate(Routes.fileUpload("LEVEL_REQUIREMENT", requirementId))
                },
                savedStateHandle = backStackEntry.savedStateHandle
            )
        }
        composable(
            route = Routes.REQUIREMENT_DETAIL,
            arguments = listOf(
                navArgument("levelId") { type = NavType.StringType },
                navArgument("requirementId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            RequirementDetailScreen(
                onBack = { navController.popBackStack() },
                onUploadReport = { lvlId, requirementId ->
                    navController.navigate(Routes.fileUpload("LEVEL_REQUIREMENT", requirementId))
                },
                onOpenEditor = { lvlId, requirementId ->
                    navController.navigate(Routes.requirementEditor(lvlId, requirementId))
                },
                onAttachTaskFile = { requirementId, taskId ->
                    navController.navigate(Routes.fileUpload("TASK_ANSWER", "$requirementId|$taskId"))
                },
                savedStateHandle = backStackEntry.savedStateHandle
            )
        }
        composable(
            route = Routes.REQUIREMENT_EDITOR,
            arguments = listOf(
                navArgument("levelId") { type = NavType.StringType },
                navArgument("requirementId") { type = NavType.StringType }
            )
        ) {
            RequirementEditorScreen(onBack = { navController.popBackStack() })
        }

        // ===== Content Manager =====
        composable(Routes.CONTENT_MANAGER) {
            ContentManagerScreen(
                onBack = { navController.popBackStack() },
                onOpenLevel = { levelId -> navController.navigate(Routes.contentLevel(levelId)) }
            )
        }
        composable(
            route = Routes.CONTENT_LEVEL,
            arguments = listOf(navArgument("levelId") { type = NavType.StringType })
        ) {
            ContentLevelScreen(
                onBack = { navController.popBackStack() },
                onOpenSection = { levelId, sectionId -> navController.navigate(Routes.contentSection(levelId, sectionId)) }
            )
        }
        composable(
            route = Routes.CONTENT_SECTION,
            arguments = listOf(
                navArgument("levelId") { type = NavType.StringType },
                navArgument("sectionId") { type = NavType.StringType }
            )
        ) {
            ContentSectionScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { levelId, requirementId ->
                    navController.navigate(Routes.requirementEditor(levelId, requirementId))
                }
            )
        }

        // ===== Chats =====
        composable(Routes.CHATS_LIST) {
            ChatsListScreen(
                onBack = { navController.popBackStack() },
                onOpenClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onOpenDM = { dmId, otherUid -> navController.navigate(Routes.dmChat(dmId, otherUid)) },
                onOpenGroup = { groupId -> navController.navigate(Routes.customGroupChat(groupId)) },
                onCreateGroup = { navController.navigate(Routes.CREATE_GROUP_CHAT) },
            )
        }
        composable(Routes.CREATE_GROUP_CHAT) {
            CreateGroupChatScreen(
                onBack = { navController.popBackStack() },
                onCreated = { groupId ->
                    navController.popBackStack()
                    navController.navigate(Routes.customGroupChat(groupId))
                },
            )
        }
        composable(
            route = Routes.CUSTOM_GROUP_CHAT,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) {
            CustomGroupChatScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DM_CHAT,
            arguments = listOf(
                navArgument("dmId") { type = NavType.StringType },
                navArgument("otherUid") { type = NavType.StringType },
            ),
        ) {
            DMChatScreen(onBack = { navController.popBackStack() })
        }

        // ===== Honors =====
        composable(Routes.HONOR_CATALOG) {
            HonorCatalogScreen(
                onBack = { navController.popBackStack() },
                onOpenHonor = { honorId -> navController.navigate(Routes.honorDetail(honorId)) }
            )
        }
        composable(
            route = Routes.HONOR_DETAIL,
            arguments = listOf(navArgument("honorId") { type = NavType.StringType })
        ) { backStackEntry ->
            val honorId = backStackEntry.arguments?.getString("honorId") ?: ""
            HonorDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { _, requirementId ->
                    navController.navigate(Routes.honorRequirementDetail(honorId, requirementId))
                }
            )
        }
        composable(
            route = Routes.HONOR_REQUIREMENT_DETAIL,
            arguments = listOf(
                navArgument("honorId") { type = NavType.StringType },
                navArgument("requirementId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val honorId = backStackEntry.arguments?.getString("honorId") ?: ""
            val requirementId = backStackEntry.arguments?.getString("requirementId") ?: ""
            HonorRequirementDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenTest = { navController.navigate(Routes.testScreen(honorId)) },
                onOpenReport = { navController.navigate(Routes.fileUpload("HONOR_REQUIREMENT", requirementId)) },
                navBackStackEntry = backStackEntry
            )
        }
        composable(Routes.MY_HONORS) {
            MyHonorsScreen(
                onBack = { navController.popBackStack() },
                onOpenHonor = { honorId -> navController.navigate(Routes.honorDetail(honorId)) }
            )
        }
        composable(
            route = Routes.TEST_SCREEN,
            arguments = listOf(navArgument("honorId") { type = NavType.StringType })
        ) {
            TestScreen(onBack = { navController.popBackStack() }, onFinished = { navController.popBackStack() })
        }

        // ===== File Upload =====
        composable(
            route = Routes.FILE_UPLOAD,
            arguments = listOf(
                navArgument("contextType") { type = NavType.StringType },
                navArgument("contextId") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            FileUploadScreen(
                onBack = { navController.popBackStack() },
                onSuccess = { result ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(FileUploadResult.SAVED_STATE_KEY, result)
                    navController.popBackStack()
                }
            )
        }

        // ===== Events & Tasks =====
        composable(Routes.EVENTS) {
            EventsScreen(
                onBack = { navController.popBackStack() },
                onOpenEvent = { eventId -> navController.navigate(Routes.eventDetail(eventId)) }
            )
        }
        composable(
            route = Routes.EVENT_DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) {
            EventDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MY_TASKS) {
            MyTasksScreen(
                onBack = { navController.popBackStack() },
                onCreateTask = { navController.navigate(Routes.CREATE_TASK) }
            )
        }

        // ===== Profile =====
        composable(Routes.TEEN_PROFILE) {
            TeenProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }

        // ===== Gamification =====
        composable(Routes.DIGITAL_UNIFORM) { DigitalUniformScreen(onNavigateBack = { navController.popBackStack() }) }
        composable(Routes.ACHIEVEMENTS) { AchievementsScreen(onNavigateBack = { navController.popBackStack() }) }

        // ===== Challenges =====
        composable(Routes.CHALLENGES) {
            ChallengesScreen(
                onNavigateBack = { navController.popBackStack() },
                onChallengeClick = { challengeId -> navController.navigate(Routes.challengeDetail(challengeId)) }
            )
        }
        composable(
            route = Routes.CHALLENGE_DETAIL,
            arguments = listOf(navArgument("challengeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val challengeId = backStackEntry.arguments?.getString("challengeId") ?: ""
            ChallengeDetailScreen(
                challengeId = challengeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ===== Reports =====
        composable(Routes.REPORT_HISTORY) {
            ReportHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onReportClick = { reportId -> navController.navigate(Routes.reportDetail(reportId)) },
                onCreateReport = { navController.navigate(Routes.CREATE_REPORT) }
            )
        }
        composable(
            route = Routes.REPORT_DETAIL,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType })
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
            ReportDetailScreen(
                reportId = reportId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ===== Chats & Moderation =====
        composable(Routes.CLUB_CHAT) { backStackEntry ->
            ClubChatScreen(
                onBack = { navController.popBackStack() },
                onAttachFile = { clubId -> navController.navigate(Routes.fileUpload("CHAT", clubId)) },
                savedStateHandle = backStackEntry.savedStateHandle
            )
        }
        composable(
            route = Routes.GROUP_CHAT,
            arguments = listOf(
                navArgument("targetType") { type = NavType.StringType },
                navArgument("targetId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val targetType = backStackEntry.arguments?.getString("targetType") ?: ""
            val targetId = backStackEntry.arguments?.getString("targetId") ?: ""
            GroupChatScreen(
                targetType = targetType,
                targetId = targetId,
                onBack = { navController.popBackStack() },
                savedStateHandle = backStackEntry.savedStateHandle
            )
        }
        composable(Routes.MODERATION) { ModerationScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SYNC) { SyncScreen(onBack = { navController.popBackStack() }) }
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", NAV)

print()
print("=" * 60)
print("Готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")