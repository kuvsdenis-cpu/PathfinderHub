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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffHomeworkContent(
    onCreateHomework: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StaffHomeworkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.groups.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Assignment, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Домашек пока нет", style = MaterialTheme.typography.bodyLarge)
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
                items(state.groups, key = { it.key }) { group ->
                    HomeworkGroupCard(
                        group = group,
                        fmt = fmt,
                        onClick = { viewModel.openGroup(group) },
                    )
                }
            }
        }

        if (state.groups.isNotEmpty() || !state.isLoading) {
            FloatingActionButton(
                onClick = onCreateHomework,
                containerColor = PathfinderBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Default.Add, "Создать домашку")
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.selectedGroup?.let { group ->
        HomeworkGroupDialog(
            group = group,
            childNames = state.childNames,
            fmt = fmt,
            onApprove = { viewModel.approve(it) },
            onReject = { viewModel.reject(it) },
            onDismiss = { viewModel.closeGroup() },
        )
    }
}

@Composable
private fun HomeworkGroupCard(
    group: HomeworkGroup,
    fmt: SimpleDateFormat,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                group.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (group.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    group.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Срок: ${fmt.format(group.dueDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (group.pendingCount > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${group.pendingCount} на проверке") },
                    )
                }
                if (group.reviewedCount > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${group.reviewedCount} проверено") },
                    )
                }
                if (group.pendingCount == 0 && group.reviewedCount == 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${group.totalCount} назначено") },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeworkGroupDialog(
    group: HomeworkGroup,
    childNames: Map<String, String>,
    fmt: SimpleDateFormat,
    onApprove: (HomeworkEntity) -> Unit,
    onReject: (HomeworkEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val pending = group.items.filter { it.status == "done" }
    val reviewed = group.items.filter { it.status == "approved" || it.status == "rejected" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(group.title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Срок: ${fmt.format(group.dueDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (group.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(group.description, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "На проверке (${pending.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (pending.isEmpty()) {
                    Text(
                        "Нет сданных работ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    pending.forEach { item ->
                        HomeworkItemRow(
                            item = item,
                            childName = childNames[item.childId] ?: item.childId,
                            onApprove = { onApprove(item) },
                            onReject = { onReject(item) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Проверено (${reviewed.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (reviewed.isEmpty()) {
                    Text(
                        "Пока пусто",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    reviewed.forEach { item ->
                        HomeworkItemRow(
                            item = item,
                            childName = childNames[item.childId] ?: item.childId,
                            onApprove = null,
                            onReject = null,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
private fun HomeworkItemRow(
    item: HomeworkEntity,
    childName: String,
    onApprove: (() -> Unit)?,
    onReject: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                childName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when (item.status) {
                    "done" -> "Сдано, ждёт проверки"
                    "approved" -> "Одобрено"
                    "rejected" -> "Возвращено"
                    "assigned" -> "Назначено"
                    else -> item.status
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (onApprove != null && onReject != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onReject) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Вернуть")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onApprove) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Одобрить")
                    }
                }
            }
        }
    }
}
