package com.pathfinder.hub.ui.learning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import java.util.Date
import java.util.UUID

/**
 * Задание fill_blank: рендер ___ → DropdownMenu с options,
 * проверка по correctAnswer (JSON массив), сохранение ответа.
 *
 * answer: String (JSON массив выбранных слов)
 * submittedAt / createdAt / updatedAt: Date
 * attachments: List<String>
 */
@Composable
fun FillBlankTaskView(
    task: RequirementTaskEntity,
    userId: String,
    clubId: String,
    existingAnswer: RequirementTaskAnswerEntity?,
    onSave: (RequirementTaskAnswerEntity) -> Unit,
) {
    val gson = remember { Gson() }
    val correctList: List<String> = remember(task.id) {
        try {
            if (task.correctAnswer.isNullOrBlank()) emptyList()
            else gson.fromJson(task.correctAnswer, Array<String>::class.java).toList()
        } catch (_: Exception) { emptyList() }
    }
    val existingList: List<String> = remember(existingAnswer?.id) {
        try {
            if (existingAnswer?.answer.isNullOrBlank()) emptyList()
            else gson.fromJson(existingAnswer.answer, Array<String>::class.java).toList()
        } catch (_: Exception) { emptyList() }
    }

    val parts = remember(task.description) {
        (task.description ?: "").split("___")
    }
    val blanksCount = (parts.size - 1).coerceAtLeast(0)

    val selected = remember(task.id, existingList) {
        mutableStateListOf<String>().apply {
            for (i in 0 until blanksCount) add(existingList.getOrNull(i) ?: "")
        }
    }

    var result by remember(task.id) { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // Рендер текста с DropdownMenu на месте ___
            Column {
                parts.forEachIndexed { idx, part ->
                    Text(part, style = MaterialTheme.typography.bodyLarge)
                    if (idx < blanksCount) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }) {
                                Text(selected.getOrNull(idx).orEmpty().ifBlank { "выбрать" })
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                task.options.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt) },
                                        onClick = {
                                            selected[idx] = opt
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val isCorrect = selected.size == correctList.size &&
                        selected.indices.all { selected[it].equals(correctList[it], ignoreCase = true) }
                    val now = Date()
                    val answer = RequirementTaskAnswerEntity(
                        id = existingAnswer?.id ?: UUID.randomUUID().toString(),
                        taskId = task.id,
                        requirementId = task.requirementId,
                        userId = userId,
                        clubId = clubId,
                        answer = gson.toJson(selected.toList()),
                        attachments = emptyList(),
                        status = if (isCorrect) "auto_approved" else "rejected",
                        autoChecked = true,
                        submittedAt = now,
                        reviewedBy = null,
                        reviewedAt = null,
                        reviewComment = null,
                        createdAt = existingAnswer?.createdAt ?: now,
                        updatedAt = now,
                        pendingSync = true,
                    )
                    onSave(answer)
                    result = if (isCorrect) "Верно!" else "Есть ошибки"
                },
                enabled = selected.all { it.isNotBlank() },
            ) { Text("Проверить") }

            result?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = if (it == "Верно!") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}