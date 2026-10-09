package com.pathfinder.hub.ui.qr.quests

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.components.DateField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: QuestEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Новый квест" else "Квест") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text("Название *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSaving
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isSaving
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.rewardPoints,
                onValueChange = viewModel::onRewardPoints,
                label = { Text("Очки за завершение квеста") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSaving
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.rewardAchievementId,
                onValueChange = viewModel::onRewardAchievement,
                label = { Text("ID ачивки за квест (опционально)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSaving
            )
            Spacer(Modifier.height(12.dp))

            Text("Срок квеста", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            DateField(
                value = state.dateStart,
                label = "Дата начала (опционально)",
                onValueChange = viewModel::onDateStart,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            DateField(
                value = state.dateEnd,
                label = "Дата окончания (пусто = бессрочный)",
                onValueChange = viewModel::onDateEnd,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.dateStart != null || state.dateEnd != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = viewModel::clearDates) {
                    Text("Сделать бессрочным")
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Станции в квесте (порядок прохождения)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Шаг 1 → Шаг 2 → ... Teen должен сканировать по порядку.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (state.selectedStationIds.isEmpty()) {
                Text(
                    "Пока не добавлено ни одной станции",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.selectedStationIds.forEachIndexed { idx, sid ->
                    val station = state.allStations.find { it.id == sid }
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${idx + 1}.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PathfinderBlue
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    station?.name ?: sid,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (station?.description?.isNotBlank() == true) {
                                    Text(
                                        station.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.moveUp(sid) },
                                enabled = idx > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, "Выше")
                            }
                            IconButton(
                                onClick = { viewModel.moveDown(sid) },
                                enabled = idx < state.selectedStationIds.size - 1
                            ) {
                                Icon(Icons.Default.ArrowDownward, "Ниже")
                            }
                            IconButton(onClick = { viewModel.toggleStation(sid) }) {
                                Icon(Icons.Default.Close, "Убрать")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::showStationDialog,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Добавить станции")
            }

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
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
                    Text(if (state.isNew) "Создать квест" else "Сохранить")
                }
            }
        }
    }

    if (state.showStationDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideStationDialog,
            title = { Text("Выберите станции") },
            text = {
                if (state.isLoadingAllStations) {
                    CircularProgressIndicator()
                } else if (state.allStations.isEmpty()) {
                    Text("В клубе пока нет станций. Создайте их в разделе «QR-станции».")
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        state.allStations.forEach { s ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = s.id in state.selectedStationIds,
                                    onCheckedChange = { viewModel.toggleStation(s.id) }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        s.type + " · " + s.rewardPoints + " очков",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::hideStationDialog) {
                    Text("Готово")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideStationDialog) {
                    Text("Отмена")
                }
            }
        )
    }
}
