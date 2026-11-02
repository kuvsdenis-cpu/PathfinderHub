package com.pathfinder.hub.ui.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
 * Задание match: левый столбец + DropdownMenu правых.
 *
 * options: плоский список [left1, right1, left2, right2, ...]
 * correctAnswer: JSON массив пар индексов [[0,1],[2,3],...]
 * answer: JSON массив выбранных правых (по индексу левого) — ["right1","right2"]
 */
@Composable
fun MatchTaskView(
    task: RequirementTaskEntity,
    userId: String,
    clubId: String,
    existingAnswer: RequirementTaskAnswerEntity?,
    onSave: (RequirementTaskAnswerEntity) -> Unit,
) {
    val gson = remember { Gson() }

    val pairs: List<Pair<String, String>> = remember(task.id, task.options) {
        task.options.chunked(2).mapNotNull { p ->
            if (p.size == 2) p[0] to p[1] else null
        }
    }

    val correctPairs: List<List<Int>> = remember(task.id, task.correctAnswer) {
        try {
            if (task.correctAnswer.isNullOrBlank()) emptyList()
            else gson.fromJson(task.correctAnswer, Array<Array<Int>>::class.java)
                .map { it.toList() }
        } catch (_: Exception) { emptyList() }
    }

    val existing: List<String> = remember(existingAnswer?.id) {
        try {
            if (existingAnswer?.answer.isNullOrBlank()) emptyList()
            else gson.fromJson(existingAnswer.answer, Array<String>::class.java).toList()
        } catch (_: Exception) { emptyList() }
    }

    val selected = remember(task.id, existing) {
        mutableStateListOf<String>().apply {
            for (i in pairs.indices) add(existing.getOrNull(i) ?: "")
        }
    }

    var result by remember(task.id) { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            task.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(pairs) { idx, pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pair.first,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }) {
                                Text(selected.getOrNull(idx).orEmpty().ifBlank { "выбрать" })
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                pairs.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.second) },
                                        onClick = {
                                            selected[idx] = p.second
                                            expanded = false
                                        },
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
                    val isCorrect = correctPairs.size == pairs.size &&
                        pairs.indices.all { i ->
                            correctPairs[i].getOrNull(1) ==
                                pairs.indexOfFirst { it.second == selected[i] }.let {
                                    if (it < 0) -1 else it * 2 + 1
                                }
                        }
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
