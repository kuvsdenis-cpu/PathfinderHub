package com.pathfinder.hub.ui.learning.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
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
    var groupsText by remember { mutableStateOf("") }
    var correctText by remember { mutableStateOf("") }
    var isRequired by remember { mutableStateOf(existing?.isRequired ?: true) }
    var matchPairsText by remember { mutableStateOf("") }

    LaunchedEffect(type, existing?.id) {
        if (type == "match" && existing != null) {
            val flat = existing.options
            if (flat.size >= 2) {
                matchPairsText = flat.chunked(2).joinToString("\n") { p ->
                    "${p.getOrNull(0) ?: ""} — ${p.getOrNull(1) ?: ""}"
                }
            }
        }
        if (type == "grouping" && existing != null) {
            try {
                val gson = Gson()
                val groups: List<Map<String, Any?>> = gson.fromJson(
                    existing.options.firstOrNull() ?: "[]",
                    object : com.google.gson.reflect.TypeToken<List<Map<String, Any?>>>() {}.type
                )
                groupsText = groups.joinToString("\n") { g ->
                    val t = g["title"] as? String ?: ""
                    val c = (g["count"] as? Number)?.toInt() ?: 0
                    "$t: $c"
                }
                val correct: Map<String, List<String>> = gson.fromJson(
                    existing.correctAnswer ?: "{}",
                    object : com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {}.type
                )
                correctText = correct.entries.joinToString("\n") { (k, v) ->
                    "$k: ${v.joinToString(", ")}"
                }
            } catch (_: Exception) { }
        }
    }

    val isChoice = type == "single_choice" || type == "multi_choice"
    val isAuto = RequirementTaskEntity.isAutoCheckType(type)
    val isFillBlank = type == "fill_blank"
    val isGrouping = type == "grouping"
    val isMatch = type == "match"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новое задание" else "Редактировать задание") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // ==================== ТИП ====================
                Text("Тип задания", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))

                Text(
                    "Автопроверка",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                TaskTypeChipsRow(
                    types = TaskTypeLabels.AUTO_TYPES,
                    selected = type,
                    onSelect = { type = it },
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "Ручная проверка",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                TaskTypeChipsRow(
                    types = TaskTypeLabels.MANUAL_TYPES,
                    selected = type,
                    onSelect = { type = it },
                )

                // Подсказка выбранного типа
                val hint = TaskTypeLabels.humanHint(type)
                if (hint.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }

                // ==================== ПОЛЯ ====================
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Заголовок") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = {
                        Text(
                            when {
                                isGrouping -> "Подсказка (необязательно)"
                                isFillBlank -> "Текст с пропусками (используйте ___)"
                                else -> "Описание (необязательно)"
                            }
                        )
                    },
                    minLines = if (isFillBlank) 4 else 2,
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

                if (isFillBlank) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = options, onValueChange = { options = it },
                        label = { Text("Слова для вставки (через запятую)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = correctAnswer, onValueChange = { correctAnswer = it },
                        label = { Text("Правильные ответы по порядку (через запятую)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isGrouping) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = groupsText, onValueChange = { groupsText = it },
                        label = { Text("Группы (название: количество, по одной в строке)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = correctText, onValueChange = { correctText = it },
                        label = { Text("Правильные ответы (группа: элементы через запятую)") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }


                if (isMatch) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = matchPairsText,
                        onValueChange = { matchPairsText = it },
                        label = { Text("Пары: левая — правая (можно : или -; по одной в строке)") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isAuto && !isFillBlank && !isGrouping && !isMatch) {
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
                enabled = title.isNotBlank() && (
                    !isFillBlank || description.isNotBlank()
                ) && (
                    !isGrouping || (groupsText.isNotBlank() && correctText.isNotBlank())
                ) && (
                    !isMatch || parseMatchPairs(matchPairsText).isNotEmpty()
                ),
                onClick = {
                    val gson = Gson()
                    val optionsList: List<String> = when {
                        isGrouping -> listOf(gson.toJson(parseGroups(groupsText)))
                        isFillBlank -> options.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        isMatch -> parseMatchPairs(matchPairsText).flatten()
                        else -> options.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                    }
                    val correct: String? = when {
                        isMatch -> {
                            val pairs = parseMatchPairs(matchPairsText)
                            gson.toJson(pairs.mapIndexed { i, _ -> listOf(i * 2, i * 2 + 1) })
                        }
                        isGrouping -> gson.toJson(parseCorrect(correctText))
                        isFillBlank -> if (correctAnswer.isBlank()) null
                            else gson.toJson(correctAnswer.split(",").map { it.trim() }.filter { it.isNotBlank() })
                        else -> correctAnswer.ifBlank { null }
                    }
                    onSave(type, title, description.ifBlank { null }, optionsList, correct, isRequired)
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskTypeChipsRow(
    types: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        types.forEach { t ->
            FilterChip(
                selected = selected == t,
                onClick = { onSelect(t) },
                label = {
                    Text(
                        TaskTypeLabels.humanLabel(t),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

private fun parseGroups(text: String): List<Map<String, Any?>> =
    text.lines().mapNotNull { line ->
        val idx = line.lastIndexOf(':')
        if (idx < 0) return@mapNotNull null
        val title = line.substring(0, idx).trim()
        val count = line.substring(idx + 1).trim().toIntOrNull() ?: return@mapNotNull null
        if (title.isBlank()) null else mapOf("title" to title, "count" to count)
    }

private fun parseCorrect(text: String): Map<String, List<String>> =
    text.lines().mapNotNull { line ->
        val idx = line.indexOf(':')
        if (idx < 0) return@mapNotNull null
        val key = line.substring(0, idx).trim()
        val vals = line.substring(idx + 1).split(",")
            .map { it.trim() }.filter { it.isNotBlank() }
        if (key.isBlank() || vals.isEmpty()) null else key to vals
    }.toMap()


private fun parseMatchPairs(text: String): List<List<String>> =
    text.lines().mapNotNull { line ->
        val idx = listOf("—", ":", "-")
            .map { line.indexOf(it) }
            .firstOrNull { it >= 0 } ?: return@mapNotNull null
        val left = line.substring(0, idx).trim()
        val right = line.substring(idx + 1).trim()
        if (left.isBlank() || right.isBlank()) null else listOf(left, right)
    }
