package com.pathfinder.hub.ui.screens.crossclub

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossClubScreen(
    onBack: () -> Unit,
    viewModel: CrossClubViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Межклубные") },
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
        CrossClubContent(
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
        )
    }
}

@Composable
fun CrossClubContent(
    modifier: Modifier = Modifier,
    viewModel: CrossClubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Отправленные (${state.outgoing.size})") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Входящие (${state.incoming.size})") }
                )
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                tab == 0 -> OutgoingList(
                    rows = state.outgoing,
                    onRevoke = viewModel::revokeInvite,
                )
                else -> IncomingList(
                    rows = state.incoming,
                    onAccept = viewModel::acceptInvite,
                    onDecline = viewModel::declineInvite,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (tab == 0) {
            FloatingActionButton(
                onClick = viewModel::openCreateDialog,
                containerColor = PathfinderBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.Add, "Создать приглашение")
            }
        }
    }

    if (state.showCreateDialog) {
        CreateInviteDialog(
            clubs = state.allClubs,
            events = state.myEvents,
            selectedClubId = state.selectedInviteeClubId,
            selectedEventId = state.selectedEventId,
            isCreating = state.isCreating,
            onClubSelect = viewModel::selectInviteeClub,
            onEventSelect = viewModel::selectEvent,
            onDismiss = viewModel::closeCreateDialog,
            onConfirm = viewModel::createInvite,
        )
    }
}

@Composable
private fun OutgoingList(
    rows: List<HostInviteRow>,
    onRevoke: (String) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyBlock("Нет отправленных приглашений", "Нажмите + чтобы пригласить клуб")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows, key = { it.invite.id }) { row ->
            InviteCard(
                title = row.inviteeClub?.name ?: row.invite.inviteeClubId,
                subtitle = row.targetTitle,
                status = row.invite.status,
                actions = {
                    if (row.invite.status == "pending") {
                        TextButton(onClick = { onRevoke(row.invite.id) }) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Отозвать")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun IncomingList(
    rows: List<HostInviteRow>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyBlock("Нет входящих приглашений", "Сюда попадут приглашения от других клубов")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows, key = { it.invite.id }) { row ->
            InviteCard(
                title = row.inviteeClub?.name ?: row.invite.hostClubId,
                subtitle = row.targetTitle,
                status = row.invite.status,
                actions = {
                    if (row.invite.status == "pending") {
                        Button(onClick = { onAccept(row.invite.id) }) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Принять")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { onDecline(row.invite.id) }) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Отклонить")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun InviteCard(
    title: String,
    subtitle: String,
    status: String,
    actions: @Composable () -> Unit,
) {
    val statusLabel = when (status) {
        "pending" -> "Ожидает"
        "accepted" -> "Принято"
        "declined" -> "Отклонено"
        "expired" -> "Истекло"
        else -> status
    }
    val statusColor = when (status) {
        "accepted" -> PathfinderGreen
        "declined" -> MaterialTheme.colorScheme.error
        "expired" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> PathfinderBlue
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, null, tint = PathfinderBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(statusLabel, color = statusColor) }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                actions()
            }
        }
    }
}

@Composable
private fun EmptyBlock(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Event, null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreateInviteDialog(
    clubs: List<com.pathfinder.hub.data.local.entity.core.ClubEntity>,
    events: List<com.pathfinder.hub.data.local.entity.planning.EventEntity>,
    selectedClubId: String?,
    selectedEventId: String?,
    isCreating: Boolean,
    onClubSelect: (String) -> Unit,
    onEventSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пригласить клуб") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Событие",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (events.isEmpty()) {
                    Text(
                        "Нет событий. Создайте событие сначала.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    events.take(10).forEach { e ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedEventId == e.id,
                                onClick = { onEventSelect(e.id) }
                            )
                            Text(e.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Клуб-получатель",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (clubs.isEmpty()) {
                    Text(
                        "Нет других клубов в конференции",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    clubs.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedClubId == c.id,
                                onClick = { onClubSelect(c.id) }
                            )
                            Text(c.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isCreating && selectedClubId != null && selectedEventId != null,
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Пригласить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
