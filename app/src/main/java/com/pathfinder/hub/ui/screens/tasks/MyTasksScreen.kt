package com.pathfinder.hub.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
    onCreateTask: () -> Unit = {}, // <-- ДОБАВЛЕНО
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
        floatingActionButton = { // <-- ДОБАВЛЕНО
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
        if (state.tasks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text("Задач пока нет")
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.tasks, key = { it.id }) { t ->
                TaskCard(t, viewModel)
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, vm: MyTasksViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleLarge)
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(task.description, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text("Статус: ${task.status}",
                style = MaterialTheme.typography.labelLarge, color = PathfinderBlue)

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.status == "assigned") {
                    Button(onClick = { vm.acceptTask(task.id) }) { Text("Принять") }
                }
                if (task.status == "accepted" || task.status == "in_progress") {
                    Button(onClick = { vm.completeTask(task.id) }) { Text("Выполнено") }
                }
                Spacer(Modifier.weight(1f))
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                IconButton(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ShareCardGenerator.generate(
                                context = ctx,
                                emoji = "\u2705",
                                title = task.title,
                                subtitle = "Статус: " + task.status,
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