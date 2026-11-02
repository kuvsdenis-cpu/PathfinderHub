package com.pathfinder.hub.ui.qr.quests

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
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
import com.pathfinder.hub.ui.screens.tests.TestsForScopeSection
import com.pathfinder.hub.ui.theme.PathfinderGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestDetailScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
    onOpenTest: (testId: String) -> Unit,
    viewModel: QuestDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Квест") },
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
        },
        bottomBar = {
            if (!state.isLoading && state.quest != null) {
                Button(
                    onClick = onScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сканировать QR")
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center
            ) { CircularProgressIndicator() }

            state.quest == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Text(
                    state.errorMessage ?: "Квест не найден",
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> {
                val q = state.quest!!
                val percent = if (state.totalSteps > 0)
                    state.completedCount.toFloat() / state.totalSteps else 0f

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Заголовок + прогресс
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.EmojiEvents, null,
                                        tint = PathfinderBlue,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        q.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (q.description.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        q.description,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { percent },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = PathfinderBlue,
                                )
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    Text(
                                        "Пройдено: ${state.completedCount} из ${state.totalSteps}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (q.rewardPoints > 0) {
                                        Icon(
                                            Icons.Default.Star, null,
                                            tint = PathfinderBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "+${q.rewardPoints} за завершение",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PathfinderBlue,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Тесты квеста
                    item {
                        TestsForScopeSection(
                            scope = "quest",
                            scopeId = q.id,
                            onOpenTest = onOpenTest,
                        )
                    }

                    // Шаги
                    item {
                        Text(
                            "Станции квеста",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    items(state.steps, key = { it.index }) { step ->
                        QuestStepCard(
                            step = step,
                            onScan = onScan,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestStepCard(
    step: QuestStep,
    onScan: () -> Unit,
) {
    val (icon, iconTint) = when {
        step.isCompleted -> Icons.Default.CheckCircle to PathfinderGreen
        step.isCurrent -> Icons.Default.RadioButtonUnchecked to PathfinderBlue
        else -> Icons.Default.Lock to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val title = when {
        step.isCompleted -> step.station?.name ?: "Станция ${step.index}"
        step.isCurrent -> step.station?.name ?: "Станция ${step.index}"
        else -> "Станция ${step.index}"
    }

    val subtitle = when {
        step.isCompleted -> "Пройдена"
        step.isCurrent -> step.station?.description ?: "Следующая к прохождению"
        else -> "Откроется после предыдущей"
    }

    val cardColor = when {
        step.isCurrent -> MaterialTheme.colorScheme.primaryContainer
        step.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${step.index}. $title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (step.isCurrent) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сканировать эту станцию")
                }
            }
        }
    }
}
