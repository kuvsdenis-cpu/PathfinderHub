package com.pathfinder.hub.ui.screens.home.parent

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
import androidx.compose.material.icons.filled.Quiz
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))

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

            // ---------- Тесты ----------
            val passedCount = state.testAttempts.count { it.passed }
            Section("Тесты · $passedCount из ${state.testAttempts.size} пройдено")
            if (state.testAttempts.isEmpty()) {
                EmptyText("Пока нет попыток тестов")
            } else {
                state.testAttempts.forEach { a ->
                    val title = when {
                        a.testId.isNullOrBlank() -> "Тест (без названия)"
                        state.testTitles[a.testId] != null -> state.testTitles[a.testId]!!
                        else -> "Тест #" + a.testId.take(8)
                    }
                    val pending = a.id in state.pendingAttemptIds
                    val status = when {
                        a.passed -> "пройден"
                        pending -> "на проверке"
                        else -> "не пройден"
                    }
                    val date = " · " + fmt.format(a.completedAt)
                    RowItem(
                        icon = Icons.Default.Quiz,
                        title = title,
                        subtitle = "${a.score}% · $status$date",
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
