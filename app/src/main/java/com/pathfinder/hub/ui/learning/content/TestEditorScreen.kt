package com.pathfinder.hub.ui.learning.content

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
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
fun TestEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenQuestions: (testId: String) -> Unit,
    viewModel: TestEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    // Диалог подтверждения удаления
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideDeleteDialog,
            title = { Text("Удалить тест?") },
            text = { Text("Тест и все его вопросы будут удалены. Действие необратимо.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideDeleteDialog) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Новый тест" else "Редактор теста") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (!state.isNew && state.canEdit) {
                        IconButton(onClick = viewModel::showDeleteDialog) {
                            Icon(Icons.Default.Delete, "Удалить")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center,
            ) { CircularProgressIndicator() }

            !state.canEdit -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center,
            ) {
                Text(
                    state.errorMessage ?: "Нет доступа",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            state.errorMessage != null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center,
            ) {
                Text(
                    state.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ---------- Название ----------
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))

                // ---------- Описание ----------
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Описание (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Spacer(Modifier.height(16.dp))

                // ---------- Scope ----------
                Text("К чему привязан тест", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                ScopeDropdown(
                    current = state.scope,
                    onSelect = viewModel::onScopeChange,
                )
                Spacer(Modifier.height(12.dp))

                // ---------- ScopeId (зависит от scope) ----------
                if (state.scope != "standalone") {
                    ScopeIdDropdown(
                        scope = state.scope,
                        currentId = state.scopeId,
                        options = state.scopeIdOptions,
                        onSelect = viewModel::onScopeIdChange,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // ---------- PassScore ----------
                OutlinedTextField(
                    value = state.passScoreText,
                    onValueChange = viewModel::onPassScoreChange,
                    label = { Text("Проходной балл, %") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
                Spacer(Modifier.height(24.dp))

                // ---------- Вопросы ----------
                if (!state.isNew) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Вопросов в тесте: ${state.questionCount}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onOpenQuestions(state.testId) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.List, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Управлять вопросами")
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ---------- Кнопка «Сохранить» ----------
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving && state.title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Save, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeDropdown(
    current: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = listOf(
        "honor" to "Специализация",
        "level" to "Ступень",
        "task" to "Задача",
        "homework" to "Домашка",
        "quest" to "Квест",
        "event" to "Событие",
        "book" to "Книга",
        "standalone" to "Без привязки",
    )
    val currentLabel = items.firstOrNull { it.first == current }?.second ?: current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Тип теста") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeIdDropdown(
    scope: String,
    currentId: String?,
    options: List<Pair<String, String>>,  // (id, displayName)
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == currentId }?.second
        ?: "Выберите"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (options.isNotEmpty()) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(scopeLabel(scope)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            enabled = options.isNotEmpty(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Нет доступных") },
                    onClick = { expanded = false },
                    enabled = false,
                )
            } else {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun scopeLabel(scope: String): String = when (scope) {
    "honor" -> "Специализация"
    "level" -> "Ступень"
    "task" -> "Задача"
    "homework" -> "Домашка"
    "quest" -> "Квест"
    "event" -> "Событие"
    "book" -> "Книга"
    else -> "ID"
}
