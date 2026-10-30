package com.pathfinder.hub.ui.screens.home.instructor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Shield
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.domain.permissions.TilePermissions
import com.pathfinder.hub.ui.screens.home.common.HomeHeaderViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import androidx.compose.material.icons.filled.School
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults

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
    onNavigateToOpenAnswers: () -> Unit = {},
    onNavigateToContentManager: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,
    onNavigateToClubInfo: (clubId: String) -> Unit = {},
    onNavigateToInfo: () -> Unit = {},
    onNavigateToSabbathSchool: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: HomeHeaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val user = state.user

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Привет, ${state.user?.firstName ?: "Наставник"}!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
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
                        val loc = state.clubLocation
                        if (!loc.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(loc, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (!state.clubId.isNullOrBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateToClubInfo(state.clubId!!) },
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Public, null,
                                tint = PathfinderBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "О клубе",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Информация, расписание, контакты",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ---------- Доп. информация ----------
            if (TilePermissions.isVisible(user, TilePermissions.INFO)) {
                item {
                    Card(
                        onClick = onNavigateToInfo,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info, null,
                                tint = PathfinderBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Доп. информация",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Форма, безопасность, атрибутика",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Работа с клубом",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (TilePermissions.isVisible(user, TilePermissions.CHECKLISTS)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Tile(Icons.Default.CheckCircle, "Проверка ответов", "Открытые задания", onNavigateToApprovals, Modifier.weight(1f))
                        Tile(Icons.Default.FactCheck, "Проверка домашки", "Сданные домашки", onNavigateToReviewHomework, Modifier.weight(1f))
                    }
                }
            }

            if (TilePermissions.isVisible(user, TilePermissions.MODERATION) ||
                TilePermissions.isVisible(user, TilePermissions.MEMBERS)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        if (TilePermissions.isVisible(user, TilePermissions.MODERATION)) {
                            Tile(Icons.Default.Shield, "Модерация", "Проверка сообщений", onNavigateToModeration, Modifier.weight(1f))
                        } else Spacer(Modifier.weight(1f))
                        if (TilePermissions.isVisible(user, TilePermissions.MEMBERS)) {
                            Tile(Icons.Default.Group, "Участники", "Список членов клуба", onNavigateToMembers, Modifier.weight(1f))
                        } else Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (TilePermissions.isVisible(user, TilePermissions.CHECKLISTS)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Tile(Icons.Default.FactCheck, "Открытые ответы", "Проверка long_text", onNavigateToOpenAnswers, Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (TilePermissions.isVisible(user, TilePermissions.HOMEWORK) ||
                TilePermissions.isVisible(user, TilePermissions.EVENTS)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        if (TilePermissions.isVisible(user, TilePermissions.HOMEWORK)) {
                            Tile(Icons.Default.Assignment, "Домашка", "Назначить", onNavigateToHomework, Modifier.weight(1f))
                        } else Spacer(Modifier.weight(1f))
                        if (TilePermissions.isVisible(user, TilePermissions.EVENTS)) {
                            Tile(Icons.Default.Event, "События", "Расписание", onNavigateToEvents, Modifier.weight(1f))
                        } else Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (TilePermissions.isVisible(user, TilePermissions.QR)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Tile(Icons.Default.QrCode, "QR-станции", "Просмотр", onNavigateToQrStations, Modifier.weight(1f))
                        Tile(Icons.Default.Chat, "Чат клуба", "Обсуждение", onNavigateToClubChat, Modifier.weight(1f))
                    }
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Tile(Icons.Default.Chat, "Чат клуба", "Обсуждение", onNavigateToClubChat, Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Tile(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }

            // ---------- Субботняя школа ----------
            item {
                Card(
                    onClick = onNavigateToSabbathSchool,
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
                            Icons.Default.School, null,
                            tint = PathfinderBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Субботняя школа",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Уроки для подростков",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (TilePermissions.isVisible(user, TilePermissions.CONTENT)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToContentManager,
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Edit, null,
                                tint = PathfinderBlue,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Контент",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Управление ступенями и тестами",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
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
