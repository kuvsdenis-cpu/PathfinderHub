package com.pathfinder.hub.ui.screens.parent_meetings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun CreateParentMeetingScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateParentMeetingViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Родительская встреча") },
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
        CreateParentMeetingContent(
            onCreated = onCreated,
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateParentMeetingContent(
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateParentMeetingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onCreated()
    }

    if (state.isLoading) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Привязать встречу к событию клуба",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        // ---------- Событие ----------
        var expanded by remember { mutableStateOf(false) }
        val selectedEvent = state.events.find { it.id == state.selectedEventId }
        val eventText = selectedEvent?.let {
            "${it.title} — ${fmt.format(it.dateStart)}"
        } ?: "Выберите событие"

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = eventText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Событие *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (state.events.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Нет событий. Создайте событие сначала.") },
                        onClick = { expanded = false }
                    )
                } else {
                    state.events.forEach { e ->
                        DropdownMenuItem(
                            text = { Text("${e.title} — ${fmt.format(e.dateStart)}") },
                            onClick = {
                                viewModel.onEventSelected(e.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.agenda,
            onValueChange = viewModel::onAgenda,
            label = { Text("Повестка встречи") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        Spacer(Modifier.height(16.dp))

        // ---------- Кому пригласить ----------
        var inviteExpanded by remember { mutableStateOf(false) }
        val inviteDisplay = when (state.groupMode) {
            "custom" -> "📁 ${state.selectedGroupLabel ?: "Группа"}"
            "manual" -> "✋ Вручную (${state.selectedParentIds.size})"
            else -> "👥 Все родители (${state.parents.size})"
        }

        ExposedDropdownMenuBox(
            expanded = inviteExpanded,
            onExpandedChange = { inviteExpanded = !inviteExpanded }
        ) {
            OutlinedTextField(
                value = inviteDisplay,
                onValueChange = {},
                readOnly = true,
                label = { Text("Кому пригласить") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = inviteExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = inviteExpanded,
                onDismissRequest = { inviteExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("👥 Все родители (${state.parents.size})") },
                    onClick = {
                        viewModel.onSelectAllParents()
                        inviteExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("✋ Вручную") },
                    onClick = {
                        viewModel.onSelectManual()
                        inviteExpanded = false
                    }
                )
                if (state.customGroups.isNotEmpty()) {
                    HorizontalDivider()
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
                                inviteExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // ---------- Ручной режим: чекбоксы ----------
        if (state.groupMode == "manual") {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Выбрано ${state.selectedParentIds.size} из ${state.parents.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::selectAllParents) { Text("Все") }
                TextButton(onClick = viewModel::clearAllParents) { Text("Сброс") }
            }
        }

        if (state.parents.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "В клубе пока нет родителей. Пригласите их через «Инвайты».",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (state.groupMode == "manual") {
            state.parents.forEach { p ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = p.id in state.selectedParentIds,
                        onCheckedChange = { viewModel.toggleParent(p.id) }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${p.firstName} ${p.lastName}".trim(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            p.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        state.errorMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    msg,
                    Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Создать встречу")
            }
        }
    }
}
