package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffTasksContent(
    onCreateTask: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StaffTasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.tasks.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.TaskAlt, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Заданий пока нет", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Нажмите + чтобы создать",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.tasks, key = { it.task.id }) { item ->
                    TaskCard(
                        item = item,
                        fmt = fmt,
                        onClick = { viewModel.openTask(item) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateTask,
            containerColor = PathfinderBlue,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, "Создать задание")
        }
    }

    state.selectedTask?.let { item ->
        TaskDetailDialog(
            item = item,
            assignees = state.assignees,
            loading = state.loadingAssignees,
            fmt = fmt,
            onDismiss = { viewModel.closeTask() },
        )
    }
}

@Composable
private fun TaskCard(
    item: TaskWithStats,
    fmt: SimpleDateFormat,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                item.task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (item.task.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            item.task.dueDate?.let { due ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "Срок: ${fmt.format(due)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text("Всего: ${item.totalCount}") })
                if (item.doneOrConfirmed > 0) {
                    AssistChip(onClick = {}, label = { Text("Выполнено: ${item.doneOrConfirmed}") })
                }
            }
        }
    }
}

@Composable
private fun TaskDetailDialog(
    item: TaskWithStats,
    assignees: List<TaskAssignee>,
    loading: Boolean,
    fmt: SimpleDateFormat,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.task.title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                item.task.dueDate?.let { due ->
                    Text(
                        "Срок: ${fmt.format(due)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.task.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.task.description, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Исполнители (${assignees.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (assignees.isEmpty()) {
                    Text(
                        "Нет исполнителей",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    assignees.forEach { a ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        a.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        statusLabel(a.status),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

private fun statusLabel(status: String): String = when (status) {
    "assigned" -> "Назначено"
    "accepted" -> "Принято"
    "done" -> "Выполнено"
    "confirmed" -> "Подтверждено"
    "rejected" -> "Отклонено"
    "cancelled" -> "Отменено"
    else -> status
}
