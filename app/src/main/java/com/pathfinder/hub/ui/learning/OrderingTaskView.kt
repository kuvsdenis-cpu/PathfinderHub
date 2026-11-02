package com.pathfinder.hub.ui.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
 * Задание ordering: элементы + кнопки ↑/↓ для изменения порядка.
 *
 * options: перемешанные элементы (List<String>)
 * correctAnswer: JSON-массив правильного порядка ["a","b","c"]
 * answer: JSON-массив текущего порядка ученика ["c","a","b"]
 *
 * Автопроверка: answer == correctAnswer (как массивы).
 */
@Composable
fun OrderingTaskView(
    task: RequirementTaskEntity,
    userId: String,
    clubId: String,
    existingAnswer: RequirementTaskAnswerEntity?,
    onSave: (RequirementTaskAnswerEntity) -> Unit,
) {
    val gson = remember { Gson() }

    val correctOrder: List<String> = remember(task.id, task.correctAnswer) {
        try {
            if (task.correctAnswer.isNullOrBlank()) emptyList()
            else gson.fromJson(task.correctAnswer, Array<String>::class.java).toList()
        } catch (_: Exception) { emptyList() }
    }

    // Текущий порядок: из existingAnswer, или из options (как пришло)
    val initial: List<String> = remember(task.id, existingAnswer?.id) {
        try {
            if (existingAnswer?.answer.isNullOrBlank()) task.options
            else gson.fromJson(existingAnswer.answer, Array<String>::class.java).toList()
        } catch (_: Exception) { task.options }
    }

    val items = remember(task.id) { mutableStateListOf<String>().apply { addAll(initial) } }
    var result by remember(task.id) { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            task.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(items) { idx, item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${idx + 1}.",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.width(28.dp),
                            )
                            Text(
                                item,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        val tmp = items[idx - 1]
                                        items[idx - 1] = items[idx]
                                        items[idx] = tmp
                                    }
                                },
                                enabled = idx > 0,
                            ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Вверх") }
                            IconButton(
                                onClick = {
                                    if (idx < items.size - 1) {
                                        val tmp = items[idx + 1]
                                        items[idx + 1] = items[idx]
                                        items[idx] = tmp
                                    }
                                },
                                enabled = idx < items.size - 1,
                            ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Вниз") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val current = items.toList()
                    val isCorrect = current == correctOrder
                    val now = Date()
                    val answer = RequirementTaskAnswerEntity(
                        id = existingAnswer?.id ?: UUID.randomUUID().toString(),
                        taskId = task.id,
                        requirementId = task.requirementId,
                        userId = userId,
                        clubId = clubId,
                        answer = gson.toJson(current),
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
