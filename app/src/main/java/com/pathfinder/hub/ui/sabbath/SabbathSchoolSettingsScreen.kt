package com.pathfinder.hub.ui.sabbath

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SabbathSchoolSettingsScreen(
    onBack: () -> Unit,
    viewModel: SabbathSchoolSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки Субботней школы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard("Год") {
                val years = listOf("A" to "А", "B" to "Б", "V" to "В", "G" to "Г")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    years.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = state.year == value,
                            onClick = { viewModel.setYear(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, years.size),
                        ) { Text(label) }
                    }
                }
            }

            SettingsCard("Квартал") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    (1..4).forEach { q ->
                        SegmentedButton(
                            selected = state.quarter == q,
                            onClick = { viewModel.setQuarter(q) },
                            shape = SegmentedButtonDefaults.itemShape(q - 1, 4),
                        ) { Text("$q") }
                    }
                }
            }

            SettingsCard("Авто-подбор урока") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Автоматически", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Текущий урок по дате",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.autoAdvance,
                        onCheckedChange = { viewModel.setAutoAdvance(it) },
                    )
                }
            }

            if (!state.autoAdvance) {
                SettingsCard("Урок вручную") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (1..13).forEach { n ->
                            FilterChip(
                                selected = state.manualLesson == n,
                                onClick = { viewModel.setManualLesson(n) },
                                label = { Text("$n") },
                            )
                        }
                    }
                }
            }

            SettingsCard("Текущий урок") {
                Text(
                    "Урок ${state.currentLesson}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                 fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
