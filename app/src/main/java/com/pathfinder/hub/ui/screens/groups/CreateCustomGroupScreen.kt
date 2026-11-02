package com.pathfinder.hub.ui.screens.groups

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCustomGroupScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CreateCustomGroupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Новая группа" else "Редактирование") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete, "Удалить",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Название группы") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSaving
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Описание (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isSaving
            )

            Spacer(Modifier.height(4.dp))
            Text("Кто входит", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            // Роли
            SectionCard(title = "Роли") {
                val roles = listOf(
                    "teen" to "Следопыты",
                    "parent" to "Родители",
                    "instructor" to "Наставники",
                    "secretary" to "Секретари",
                    "director" to "Директора",
                )
                roles.forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = key in state.roles,
                            onCheckedChange = { viewModel.toggleRole(key) },
                            enabled = !state.isSaving
                        )
                        Text(label)
                    }
                }
            }

            // Ступени
            SectionCard(title = "Завершённые ступени (ИЛИ)") {
                if (state.allLevels.isEmpty()) {
                    Text(
                        "Ступени не загружены",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.allLevels.forEach { lvl ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = lvl.id in state.levelIds,
                                onCheckedChange = { viewModel.toggleLevel(lvl.id) },
                                enabled = !state.isSaving
                            )
                            Text(lvl.name)
                        }
                    }
                }
            }

            // Специализации
            SectionCard(title = "Завершённые специализации (ИЛИ)") {
                if (state.allHonors.isEmpty()) {
                    Text(
                        "Специализации не загружены",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.allHonors.forEach { h ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = h.id in state.honorIds,
                                onCheckedChange = { viewModel.toggleHonor(h.id) },
                                enabled = !state.isSaving
                            )
                            Text(h.name)
                        }
                    }
                }
            }

            // Статусы
            SectionCard(title = "Статус") {
                val statuses = listOf(
                    "active" to "Активные",
                    "pending" to "Ожидающие",
                    "blocked" to "Заблокированные",
                )
                statuses.forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = key in state.statuses,
                            onCheckedChange = { viewModel.toggleStatus(key) },
                            enabled = !state.isSaving
                        )
                        Text(label)
                    }
                }
            }

            // Owner
            SectionCard(title = "Владелец клуба") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.ownerFilter == "any",
                        onClick = { viewModel.setOwnerFilter("any") },
                        enabled = !state.isSaving
                    )
                    Text("Не важно")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.ownerFilter == "yes",
                        onClick = { viewModel.setOwnerFilter("yes") },
                        enabled = !state.isSaving
                    )
                    Text("Только владельцы")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.ownerFilter == "no",
                        onClick = { viewModel.setOwnerFilter("no") },
                        enabled = !state.isSaving
                    )
                    Text("Только не-владельцы")
                }
            }

            // Предпросмотр
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = PathfinderBlue.copy(alpha = 0.10f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Сейчас в группе",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    if (state.previewCount == null) {
                        Text("…", style = MaterialTheme.typography.headlineMedium)
                    } else {
                        Text(
                            "${state.previewCount} чел.",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PathfinderBlue
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving && state.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (state.isNew) "Создать" else "Сохранить")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить группу?") },
            text = { Text("Действие необратимо. Если группа используется в задачах/событиях, лучше отправить в архив.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; viewModel.deleteGroup() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}
