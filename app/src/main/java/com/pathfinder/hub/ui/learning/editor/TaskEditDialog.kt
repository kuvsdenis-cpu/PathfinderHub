package com.pathfinder.hub.ui.learning.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity

@Composable
fun TaskEditDialog(
    existing: RequirementTaskEntity?,
    onDismiss: () -> Unit,
    onSave: (
        type: String, title: String, description: String?,
        options: List<String>, correctAnswer: String?,
        isRequired: Boolean
    ) -> Unit,
) {
    var type by remember { mutableStateOf(existing?.type ?: "short_text") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var options by remember { mutableStateOf(existing?.options?.joinToString("\n") ?: "") }
    var correctAnswer by remember { mutableStateOf(existing?.correctAnswer ?: "") }
    var isRequired by remember { mutableStateOf(existing?.isRequired ?: true) }

    val types = listOf(
        "short_text", "long_text", "single_choice", "multi_choice",
        "number", "date", "checkbox", "file", "cipher", "crossword"
    )
    val isChoice = type == "single_choice" || type == "multi_choice"
    val isAuto = RequirementTaskEntity.isAutoCheckType(type)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новое задание" else "Редактировать задание") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Тип", style = MaterialTheme.typography.labelMedium)
                types.chunked(3).forEach { row ->
                    Row {
                        row.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(t, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Заголовок") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Описание (необязательно)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isChoice) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = options, onValueChange = { options = it },
                        label = { Text("Варианты (по одному в строке)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isAuto) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = correctAnswer, onValueChange = { correctAnswer = it },
                        label = {
                            Text(
                                if (isChoice) "Индекс правильного (0 = первый)"
                                else "Правильный ответ"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row {
                    Checkbox(checked = isRequired, onCheckedChange = { isRequired = it })
                    Text("Обязательное", modifier = Modifier.padding(start = 4.dp, top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        type, title, description.ifBlank { null },
                        options.split("\n").map { it.trim() }.filter { it.isNotBlank() },
                        correctAnswer.ifBlank { null },
                        isRequired
                    )
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
