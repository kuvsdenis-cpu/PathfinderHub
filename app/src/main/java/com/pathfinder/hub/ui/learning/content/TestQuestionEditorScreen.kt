package com.pathfinder.hub.ui.learning.content

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
fun TestQuestionEditorScreen(
    onBack: () -> Unit,
    viewModel: TestQuestionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Диалог редактирования вопроса
    state.editingQuestion?.let { q ->
        QuestionDialog(
            initial = q,
            onSave = { saved -> viewModel.saveQuestion(saved) },
            onDismiss = viewModel::cancelEdit,
        )
    }

    // Диалог подтверждения удаления
    state.pendingDeleteId?.let { _ ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Удалить вопрос?") },
            text = { Text("Вопрос будет удалён. Действие необратимо.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вопросы теста") },
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
        },
        floatingActionButton = {
            if (state.canEdit) {
                FloatingActionButton(onClick = viewModel::createQuestion) {
                    Icon(Icons.Default.Add, "Добавить вопрос")
                }
            }
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
            ) { Text(
                state.errorMessage ?: "Нет доступа",
                color = MaterialTheme.colorScheme.error,
            ) }

            state.errorMessage != null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center,
            ) { Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error) }

            state.questions.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center,
            ) {
                Text(
                    "Вопросов нет. Нажмите + чтобы добавить.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.questions, key = { _, q -> q.id }) { idx, q ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "№${idx + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PathfinderBlue,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    typeLabel(q.type),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${q.points} балл(ов)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(q.text, style = MaterialTheme.typography.bodyLarge)
                            if (q.options.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                q.options.forEach { opt ->
                                    Text(
                                        "• $opt" + if (opt == q.correctAnswer) "  ✓" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (opt == q.correctAnswer)
                                            PathfinderBlue
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { viewModel.editQuestion(q) }) {
                                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Изменить")
                                }
                                TextButton(onClick = { viewModel.askDelete(q.id) }) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Удалить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    "choice" -> "С выбором"
    "long_text" -> "Открытый ответ"
    else -> type
}

@Composable
private fun QuestionDialog(
    initial: QuestionDraft,
    onSave: (QuestionDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.text) }
    var type by remember { mutableStateOf(initial.type) }
    var pointsText by remember { mutableStateOf(initial.points.toString()) }
    var options by remember { mutableStateOf(initial.options.joinToString("\n")) }
    var correct by remember { mutableStateOf(initial.correctAnswer) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Новый вопрос" else "Редактирование") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст вопроса") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Тип:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    listOf("choice" to "С выбором", "long_text" to "Открытый")
                        .forEach { (v, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = type == v,
                                    onClick = { type = v },
                                )
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                }
                if (type == "choice") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = options,
                        onValueChange = { options = it },
                        label = { Text("Варианты (по одному на строку)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = correct,
                        onValueChange = { correct = it },
                        label = { Text("Правильный ответ") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Баллы") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmedText = text.trim()
                if (trimmedText.isBlank()) {
                    error = "Введите текст вопроса"
                    return@Button
                }
                val points = pointsText.toIntOrNull() ?: 1
                if (points < 1) {
                    error = "Баллы должны быть ≥ 1"
                    return@Button
                }
                val opts = if (type == "choice") {
                    options.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                } else emptyList()
                val ans = if (type == "choice") correct.trim() else ""
                if (type == "choice") {
                    if (opts.size < 2) {
                        error = "Минимум 2 варианта"
                        return@Button
                    }
                    if (ans.isBlank() || ans !in opts) {
                        error = "Правильный ответ должен быть одним из вариантов"
                        return@Button
                    }
                }
                onSave(initial.copy(
                    text = trimmedText,
                    type = type,
                    options = opts,
                    correctAnswer = ans,
                    points = points,
                ))
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
