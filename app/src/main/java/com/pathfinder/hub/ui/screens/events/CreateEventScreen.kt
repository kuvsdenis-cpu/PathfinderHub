package com.pathfinder.hub.ui.screens.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.components.DateTimeField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новое событие") },
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
            Text("Основная информация", style = MaterialTheme.typography.titleLarge)
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

            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::onLocationChange,
                label = { Text("Место проведения") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )

            Spacer(Modifier.height(16.dp))
            Text("Координаты (необязательно)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.latitude,
                    onValueChange = viewModel::onLatitudeChange,
                    label = { Text("Широта") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = state.longitude,
                    onValueChange = viewModel::onLongitudeChange,
                    label = { Text("Долгота") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---------- A2: Кому назначить ----------
            var expanded by remember { mutableStateOf(false) }
            val assignedDisplay = when {
                state.selectedGroupLabel != null -> "📋 ${state.selectedGroupLabel}"
                state.assignedTo.isNotBlank() -> {
                    val u = state.clubMembers.find { it.id == state.assignedTo }
                    u?.let { "${it.firstName} ${it.lastName} (${CreateEventViewModel.roleName(it.role)})" }
                        ?: "Выберите получателя"
                }
                else -> "Выберите получателя"
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = assignedDisplay,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Кому назначить") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !state.isLoading),
                    enabled = !state.isLoading
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // Мои группы (B4)
                    if (state.customGroups.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "МОИ ГРУППЫ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {},
                            enabled = false,
                        )
                        state.customGroups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text("📁 ${g.name}") },
                                onClick = {
                                    viewModel.onSelectCustomGroup(g)
                                    expanded = false
                                }
                            )
                        }
                        HorizontalDivider()
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                "БЫСТРЫЕ ГРУППЫ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                    viewModel.quickGroups.forEach { g ->
                        DropdownMenuItem(
                            text = { Text("📋 ${g.label}") },
                            onClick = {
                                viewModel.onSelectQuickGroup(g)
                                expanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("⚙ Своя группа (роли)") },
                        onClick = {
                            viewModel.showCustomGroupDialog()
                            expanded = false
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                "КОНКРЕТНЫЙ ПОЛЬЗОВАТЕЛЬ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                    if (state.clubMembers.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("В клубе пока нет участников") },
                            onClick = { expanded = false },
                            enabled = false,
                        )
                    } else {
                        state.clubMembers.forEach { u ->
                            DropdownMenuItem(
                                text = {
                                    Text("${u.firstName} ${u.lastName} (${CreateEventViewModel.roleName(u.role)})")
                                },
                                onClick = {
                                    viewModel.onAssignedToChange(u.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Дата и время", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            DateTimeField(
                value = state.dateStart,
                label = "Начало",
                onValueChange = viewModel::onDateStartChange,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            DateTimeField(
                value = state.dateEnd,
                label = "Окончание",
                onValueChange = viewModel::onDateEndChange,
                enabled = !state.isLoading
            )

            if (state.errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
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
                onClick = viewModel::createEvent,
                enabled = !state.isLoading && state.title.isNotBlank() &&
                        (state.assignedTo.isNotBlank() || !state.targetGroup.isNullOrBlank()),

                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Создать событие")
                }
            }
        }
    }

    // ---------- A2: диалог кастомной группы ----------
    if (state.showCustomGroupDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideCustomGroupDialog,
            title = { Text("Своя группа") },
            text = {
                Column {
                    Text("Отметьте роли, которые попадут в группу:")
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "teen" to "Следопыты",
                        "parent" to "Родители",
                        "instructor" to "Наставники",
                        "secretary" to "Секретари",
                        "director" to "Директора",
                    ).forEach { (role, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = role in state.customSelectedRoles,
                                onCheckedChange = { viewModel.toggleCustomRole(role) }
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmCustomGroup,
                    enabled = state.customSelectedRoles.isNotEmpty()
                ) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideCustomGroupDialog) {
                    Text("Отмена")
                }
            }
        )
    }
}
