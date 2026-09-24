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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
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
    viewModel: TeenHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pathfinder Hub") },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onNavigateToProfile) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
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
            // Приветствие
            val firstName = state.user?.firstName ?: "Следопыт"
            Text(
                text = "Привет, $firstName!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // Карточка текущей ступени
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

            // Кнопки разделов
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionTile(
                    icon = Icons.Default.MenuBook,
                    label = "Ступени",
                    onClick = onNavigateToLevels,
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = Icons.Default.WorkspacePremium,
                    label = "Специализации",
                    onClick = onNavigateToHonors,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionTile(
                    icon = Icons.Default.CheckCircle,
                    label = "Мои нашивки",
                    onClick = onNavigateToMyHonors,
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = Icons.Default.Event,
                    label = "События",
                    onClick = onNavigateToEvents,
                    modifier = Modifier.weight(1f)
                )
            }

            // Задачи
            SectionCard(
                icon = Icons.Default.CheckCircle,
                title = "Мои задачи",
                actionLabel = "Все задачи",
                onActionClick = onNavigateToTasks
            ) {
                if (state.tasksToday.isEmpty()) {
                    Text(
                        text = "Задач пока нет",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

            // Ближайшее событие
            SectionCard(
                icon = Icons.Default.Event,
                title = "Ближайшее событие",
                actionLabel = "Все события",
                onActionClick = onNavigateToEvents
            ) {
                val event = state.nextEvent
                if (event == null) {
                    Text(
                        text = "Пока нет запланированных событий",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    val fmt = SimpleDateFormat("d MMMM, HH:mm", Locale("ru"))
                    Text(
                        text = fmt.format(event.dateStart),
                        style = MaterialTheme.typography.bodyLarge
                    )
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

            // Достижения
            SectionCard(
                icon = Icons.Default.Star,
                title = "Последние достижения",
                actionLabel = "Мои нашивки",
                onActionClick = onNavigateToMyHonors
            ) {
                if (state.recentAchievements.isEmpty()) {
                    Text(
                        text = "Достижений пока нет — вперёд к первым!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            Text(
                                text = ach.achievementId,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PathfinderBlue
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (actionLabel != null && onActionClick != null) {
                    androidx.compose.material3.TextButton(onClick = onActionClick) {
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