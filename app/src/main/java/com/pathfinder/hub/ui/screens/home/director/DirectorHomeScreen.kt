package com.pathfinder.hub.ui.screens.home.director

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
