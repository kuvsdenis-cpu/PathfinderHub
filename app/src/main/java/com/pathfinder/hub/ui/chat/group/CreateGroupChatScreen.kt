package com.pathfinder.hub.ui.chat.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupChatScreen(
    onBack: () -> Unit,
    onCreated: (groupId: String) -> Unit,
    viewModel: CreateGroupChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.createdGroupId) {
        state.createdGroupId?.let { id ->
            onCreated(id)
            viewModel.clearCreated()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая группа") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Название группы") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCreating,
            )
            Spacer(Modifier.height(16.dp))

            // B5.1: Мои группы (если есть)
            if (state.customGroups.isNotEmpty()) {
                Text(
                    "Мои группы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.customGroups, key = { it.id }) { g ->
                        OutlinedButton(
                            onClick = { viewModel.fillFromGroup(g.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("\uD83D\uDCC1 ${g.name}")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = viewModel::clearMembers) { Text("Снять всех") }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text("Участники (${state.selectedIds.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.members, key = { it.id }) { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.selectedIds.contains(m.id),
                            onCheckedChange = { viewModel.toggleMember(m.id) },
                            enabled = !state.isCreating,
                        )
                        Column(Modifier.weight(1f)) {
                            Text("${m.firstName} ${m.lastName}".trim(),
                                style = MaterialTheme.typography.bodyLarge)
                            Text(m.role,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::create,
                enabled = state.canCreate,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Создать группу")
                }
            }
        }
    }
}
