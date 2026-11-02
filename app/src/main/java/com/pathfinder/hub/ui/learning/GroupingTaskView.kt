package com.pathfinder.hub.ui.learning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Задание grouping: список групп, в каждой — N полей.
 * answer — JSON-объект: {"группа":["элемент1","элемент2",...]}.
 */
@Composable
fun GroupingTaskView(
    task: com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity,
    existingAnswer: String?,
    onSaveDraft: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    val gson = remember { Gson() }

    val groups: List<Map<String, Any?>> = remember(task.id) {
        try {
            gson.fromJson(
                task.options.firstOrNull() ?: "[]",
                object : TypeToken<List<Map<String, Any?>>>() {}.type
            )
        } catch (_: Exception) { emptyList() }
    }

    val initial: Map<String, List<String>> = remember(task.id, existingAnswer) {
        try {
            if (existingAnswer.isNullOrBlank()) emptyMap()
            else gson.fromJson(
                existingAnswer,
                object : TypeToken<Map<String, List<String>>>() {}.type
            )
        } catch (_: Exception) { emptyMap() }
    }

    val selected = remember(task.id) {
        mutableStateMapOf<String, MutableList<String>>().apply {
            groups.forEach { g ->
                val title = g["title"] as? String ?: return@forEach
                val count = (g["count"] as? Number)?.toInt() ?: 0
                val cur = initial[title].orEmpty()
                put(title, MutableList(count) { i -> cur.getOrNull(i) ?: "" })
            }
        }
    }

    var result by remember(task.id) { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            task.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))

            groups.forEach { g ->
                val title = g["title"] as? String ?: return@forEach
                val count = (g["count"] as? Number)?.toInt() ?: 0
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                for (i in 0 until count) {
                    OutlinedTextField(
                        value = selected[title]?.getOrNull(i) ?: "",
                        onValueChange = { v ->
                            selected[title]?.let { list ->
                                if (i < list.size) list[i] = v
                            }
                        },
                        label = { Text("${i + 1}.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val json = gson.toJson(selected.mapValues { it.value.toList() })
                        onSaveDraft(json)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Черновик") }

                Button(
                    onClick = {
                        val json = gson.toJson(selected.mapValues { it.value.toList() })
                        onSubmit(json)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Отправить") }
            }

            result?.let {
                Spacer(Modifier.height(8.dp))
                Text(it)
            }
        }
    }
}
