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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageParentsScreen(
    modifier: Modifier = Modifier,
    viewModel: ManageParentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null && state.parentsWithChildren.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp), Alignment.Center
                ) {
                    Text(state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                }
                state.parentsWithChildren.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp), Alignment.Center
                ) {
                    Text("В клубе нет родителей", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.parentsWithChildren, key = { it.parent.id }) { pwc ->
                        ParentCard(
                            pwc = pwc,
                            onLink = { viewModel.openLinkDialog(pwc) },
                            onRevokeChild = { parentId, childId, name ->
                                viewModel.openRevokeDialogByChild(parentId, childId, name)
                            },
                        )
                    }
                }
            }
        }
    }

    if (state.showLinkDialog) {
        val target = state.linkTarget
        val available = state.allChildren.filter { c ->
            target?.children?.none { it.id == c.id } == true
        }
        var query by remember { mutableStateOf("") }
        val filtered = available.filter { c ->
            query.isBlank() || "${c.firstName} ${c.lastName}".contains(query, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissLinkDialog,
            title = { Text("Привязать ребёнка к ${target?.parent?.firstName.orEmpty()}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Поиск") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (filtered.isEmpty()) {
                        Text("Нет доступных детей", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(modifier = Modifier.height(240.dp)) {
                            items(filtered, key = { it.id }) { c ->
                                FilterChip(
                                    selected = state.linkSelectedChildId == c.id,
                                    onClick = { viewModel.selectChildForLink(c.id) },
                                    label = { Text("${c.firstName} ${c.lastName}".trim()) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmLink,
                    enabled = state.linkSelectedChildId != null,
                ) { Text("Привязать") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLinkDialog) { Text("Отмена") }
            }
        )
    }

    if (state.showRevokeDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRevokeDialog,
            title = { Text("Отвязать ${state.revokeTargetName}?") },
            text = {
                Column {
                    Text(
                        "Связь будет помечена как отозванная. Ребёнок пропадёт у родителя.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.revokeReason,
                        onValueChange = viewModel::onRevokeReasonChange,
                        label = { Text("Причина (опционально)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRevoke) {
                    Text("Отвязать", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRevokeDialog) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ParentCard(
    pwc: ParentWithChildren,
    onLink: () -> Unit,
    onRevokeChild: (parentId: String, childId: String, name: String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = PathfinderBlue, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${pwc.parent.firstName} ${pwc.parent.lastName}".trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        pwc.parent.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (pwc.children.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Нет привязанных детей",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                pwc.children.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "• ${c.firstName} ${c.lastName}".trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            onRevokeChild(
                                pwc.parent.id,
                                c.id,
                                "${c.firstName} ${c.lastName}".trim(),
                            )
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Отвязать",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLink) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(Modifier.width(4.dp))
                Text("Привязать ребёнка")
            }
        }
    }
}