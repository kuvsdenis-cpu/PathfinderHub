package com.pathfinder.hub.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.pathfinder.hub.ui.share.ShareCardGenerator
import com.pathfinder.hub.ui.share.ShareCardHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    onBack: () -> Unit,
    onCreateTask: () -> Unit = {},
    viewModel: MyTasksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои задачи") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTask,
                containerColor = PathfinderBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, "Создать задачу")
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.activeTab) {
                Tab(
                    selected = state.activeTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = { Text("Активные (${state.tasks.size})") }
                )
                Tab(
                    selected = state.activeTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = { Text("Архив (${state.archived.size})") }
                )
            }
            val current = if (state.activeTab == 0) state.tasks else state.archived
            if (current.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        if (state.activeTab == 0)
                            "Задач пока нет"
                        else
                            "Архив пуст. Задачи удаляются через 90 дней."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(current, key = { it.task.id }) { item ->
                        if (state.activeTab == 1) {
                            ArchivedTaskCard(item.task, onRestore = { viewModel.restoreTask(item.task.id) })
                        } else {
                            TaskCard(item, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedTaskCard(task: TaskEntity, onRestore: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                task.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.archivedAt != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "В архиве с: ${java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("ru")).format(task.archivedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Восстановить")
            }
        }
    }
}

@Composable
private fun TaskCard(item: TaskWithAssignment, vm: MyTasksViewModel) {
    val task = item.task
    val status = item.assignmentStatus ?: task.status
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleLarge)
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(task.description, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text("Статус: ${statusName(status)}",
                style = MaterialTheme.typography.labelLarge, color = PathfinderBlue)

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status == "assigned") {
                    Button(onClick = { vm.acceptTask(task.id) }) { Text("Принять") }
                }
                if (status == "accepted" || status == "in_progress") {
                    Button(onClick = { vm.completeTask(task.id) }) { Text("Выполнено") }
                }
                Spacer(Modifier.weight(1f))
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                IconButton(onClick = {
                    scope.launch {
                        val payload = com.pathfinder.hub.ui.qr.EntityQrHelper.taskPayload(task.id)
                        val file = withContext(Dispatchers.IO) {
                            com.pathfinder.hub.ui.qr.EntityQrHelper.generatePngForPayload(
                                ctx, payload, "task_${'$'}{task.id}.png"
                            )
                        }
                        com.pathfinder.hub.ui.qr.EntityQrHelper.open(ctx, file)
                    }
                }) {
                    Icon(Icons.Default.QrCode, "Показать QR", tint = PathfinderBlue)
                }
                IconButton(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ShareCardGenerator.generate(
                                context = ctx,
                                emoji = "\u2705",
                                title = task.title,
                                subtitle = "Статус: " + status,
                                userName = null,
                            )
                        }
                        ShareCardHelper.share(ctx, file, "Задача в Pathfinder Hub: " + task.title)
                    }
                }) {
                    Icon(Icons.Default.Share, "Поделиться", tint = PathfinderBlue)
                }
            }
        }
    }
}

private fun statusName(status: String): String = when (status) {
    "assigned" -> "Назначена"
    "accepted" -> "Принята"
    "in_progress" -> "В работе"
    "done" -> "Выполнена"
    "confirmed" -> "Подтверждена"
    "rejected" -> "Отклонена"
    "cancelled" -> "Отменена"
    else -> status
}
