package com.pathfinder.hub.ui.screens.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.components.DateField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateTaskViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новое задание") },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Параметры задания", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            // Исполнитель
            var expanded by remember { mutableStateOf(false) }
            val assignedUserName = state.clubMembers
                .find { it.id == state.assignedTo }
                ?.let { "${it.firstName} ${it.lastName}" }
                ?: "Выберите исполнителя"

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = assignedUserName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Исполнитель") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    state.clubMembers.forEach { user ->
                        DropdownMenuItem(
                            text = {
                                Text("${user.firstName} ${user.lastName} (${getRoleName(user.role)})")
                            },
                            onClick = {
                                viewModel.onAssignedToChange(user.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Срок — календарь
            DateField(
                value = state.dueDate,
                label = "Срок выполнения (необязательно)",
                onValueChange = viewModel::onDueDateChange,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Приоритет
            var priorityExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = priorityExpanded,
                onExpandedChange = { priorityExpanded = !priorityExpanded }
            ) {
                OutlinedTextField(
                    value = when (state.priority) {
                        "high" -> "Высокий"
                        "normal" -> "Обычный"
                        "low" -> "Низкий"
                        else -> state.priority
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Приоритет") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                )
                ExposedDropdownMenu(
                    expanded = priorityExpanded,
                    onDismissRequest = { priorityExpanded = false }
                ) {
                    listOf(
                        "high" to "Высокий",
                        "normal" to "Обычный",
                        "low" to "Низкий",
                    ).forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.onPriorityChange(value)
                                priorityExpanded = false
                            }
                        )
                    }
                }
            }

            if (state.errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::createTask,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Назначить задание")
                }
            }
        }
    }
}

private fun getRoleName(role: String): String = when (role) {
    "teen" -> "Следопыт"
    "parent" -> "Родитель"
    "instructor" -> "Наставник"
    "director" -> "Директор"
    "secretary" -> "Секретарь"
    "conference" -> "Конференция"
    else -> role
}
