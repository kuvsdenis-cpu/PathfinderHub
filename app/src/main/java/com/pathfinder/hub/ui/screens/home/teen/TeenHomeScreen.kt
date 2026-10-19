package com.pathfinder.hub.ui.screens.home.teen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.components.EmailVerificationBanner
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeenHomeScreen(
    onNavigateToLevels: () -> Unit = {},
    onNavigateToHonors: () -> Unit = {},
    onNavigateToMyHonors: () -> Unit = {},
    onNavigateToEvents: () -> Unit = {},
    onNavigateToTasks: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToDigitalUniform: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToChallenges: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToClubChat: () -> Unit = {},
    onNavigateToChats: () -> Unit = {},
    onNavigateToCertificates: () -> Unit = {},
    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToActivity: () -> Unit = {},
    onNavigateToCrossClub: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToClubInfo: (clubId: String) -> Unit = {},
    onNavigateToBible: () -> Unit = {},
    onNavigateToPearls: () -> Unit = {},
    onNavigateToReadingPlan: () -> Unit = {},
    onNavigateToBooks: () -> Unit = {},
    viewModel: TeenHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pathfinder Hub") },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Уведомления",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Профиль",
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
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Приветствие
            item {
                val firstName = state.user?.firstName ?: "Следопыт"
                Text(
                    text = "Привет, $firstName!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Плашка "Почта не подтверждена"
            if (state.user?.emailVerified == false) {
                item {
                    EmailVerificationBanner(
                        isSending = state.resendingVerification,
                        message = state.verificationSentMessage,
                        onResend = viewModel::resendVerificationEmail
                    )
                }
            }

            // Карточка текущей ступени
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Текущая ступень",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.currentLevel?.name ?: "Все ступени пройдены!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.levelProgressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = PathfinderBlue
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Пройдено ступеней: ${state.completedLevels} из ${state.totalLevels}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Клуб
            if (!state.user?.clubId.isNullOrBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateToClubInfo(state.user!!.clubId!!) },
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

            // Кнопки разделов - Ряд 1
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionTile(Icons.Default.MenuBook, "Ступени", onNavigateToLevels, Modifier.weight(1f))
                    ActionTile(Icons.Default.WorkspacePremium, "Специализации", onNavigateToHonors, Modifier.weight(1f))
                }
            }
            // Кнопки разделов - Ряд 2
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionTile(Icons.Default.Star, "Цифровая форма", onNavigateToDigitalUniform, Modifier.weight(1f))
                    ActionTile(Icons.Default.CheckCircle, "Достижения", onNavigateToAchievements, Modifier.weight(1f))
                }
            }
            // Кнопки разделов - Ряд 3
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionTile(Icons.Default.EmojiEvents, "Челленджи", onNavigateToChallenges, Modifier.weight(1f))
                    ActionTile(Icons.Default.AccountBalance, "Отчёты", onNavigateToReports, Modifier.weight(1f))
                }
            }
            // Кнопки разделов - Ряд 4
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionTile(Icons.Default.Chat, "Чаты", onNavigateToChats, Modifier.weight(1f))
                    ActionTile(Icons.Default.Description, "Сертификаты", onNavigateToCertificates, Modifier.weight(1f))
                    ActionTile(Icons.Default.QrCodeScanner, "QR", onNavigateToQrScanner, Modifier.weight(1f))
                }
            }
            // Кнопки разделов - Ряд 5
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionTile(Icons.Default.Bolt, "Активность", onNavigateToActivity, Modifier.weight(1f))
                    ActionTile(Icons.Default.Public, "Межклубные", onNavigateToCrossClub, Modifier.weight(1f))
                }
            }

            // Библия
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToBible,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Book, null,
                            tint = PathfinderBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Библия",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Чтение и закладки",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Жемчужины
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToPearls,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoStories, null,
                            tint = PathfinderBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Жемчужины",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Стихи недели на 2026 год",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Книги
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToBooks,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoStories, null,
                            tint = PathfinderBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Книги", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold)
                            Text("Чтение и задания по книгам",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // План чтения
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToReadingPlan,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.CalendarMonth, null,
                            tint = PathfinderBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "План чтения",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Библия и труды Духа Пророчества на 2026",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Задачи
            item {
                SectionCard(
                    icon = Icons.Default.CheckCircle,
                    title = "Мои задачи",
                    actionLabel = "Все задачи",
                    onActionClick = onNavigateToTasks
                ) {
                    if (state.tasksToday.isEmpty()) {
                        Text("Задач пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.tasksToday.take(3).forEach { task ->
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = statusLabel(task.status),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (state.tasksToday.size > 3) {
                            Text(
                                text = "и ещё ${state.tasksToday.size - 3}…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Ближайшее событие
            item {
                SectionCard(
                    icon = Icons.Default.Event,
                    title = "Ближайшее событие",
                    actionLabel = "Все события",
                    onActionClick = onNavigateToEvents
                ) {
                    val event = state.nextEvent
                    if (event == null) {
                        Text("Пока нет запланированных событий", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        val fmt = SimpleDateFormat("d MMMM, HH:mm", Locale.forLanguageTag("ru"))
                        Text(text = fmt.format(event.dateStart), style = MaterialTheme.typography.bodyLarge)
                        if (event.location.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = event.location,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Достижения
            item {
                SectionCard(
                    icon = Icons.Default.Star,
                    title = "Последние достижения",
                    actionLabel = "Все достижения",
                    onActionClick = onNavigateToAchievements
                ) {
                    if (state.recentAchievements.isEmpty()) {
                        Text("Достижений пока нет — вперёд к первым!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.recentAchievements.forEach { ach ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = PathfinderBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(text = ach.achievementId, style = MaterialTheme.typography.bodyLarge)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(72.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = PathfinderBlue)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (actionLabel != null && onActionClick != null) {
                    TextButton(onClick = onActionClick) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "assigned" -> "Назначена"
    "accepted" -> "Принята"
    "in_progress" -> "В работе"
    "done" -> "Выполнена"
    "confirmed" -> "Подтверждена"
    "rejected" -> "Отклонена"
    "cancelled" -> "Отменена"
    else -> status
}
