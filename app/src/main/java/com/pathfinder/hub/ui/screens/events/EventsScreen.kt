package com.pathfinder.hub.ui.screens.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.ui.screens.home.director.ClubContextViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onCreateEvent: (() -> Unit)? = null,
    viewModel: EventsViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("События") },
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
        EventsContent(
            onOpenEvent = onOpenEvent,
            onCreateEvent = onCreateEvent,
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
            clubContextViewModel = clubContextViewModel,
        )
    }
}

@Composable
fun EventsContent(
    onOpenEvent: (String) -> Unit,
    onCreateEvent: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: EventsViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clubState by clubContextViewModel.state.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMMM, HH:mm", Locale.forLanguageTag("ru")) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = state.activeTab) {
                Tab(
                    selected = state.activeTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = { Text("Активные (${state.events.size})") }
                )
                Tab(
                    selected = state.activeTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = { Text("Архив (${state.archived.size})") }
                )
            }
            val current = if (state.activeTab == 0) state.events else state.archived
            if (current.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (state.activeTab == 0)
                                "Пока нет запланированных событий"
                            else
                                "Архив пуст.\nАрхивированные события удаляются через 90 дней."
                        )
                        if (state.activeTab == 0 && onCreateEvent != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Нажмите + чтобы создать первое событие",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current, key = { it.id }) { e ->
                        if (state.activeTab == 1) {
                            ArchivedEventCard(e, fmt.format(e.dateStart), onRestore = {
                                viewModel.restoreEvent(e.id)
                            })
                        } else {
                            EventCard(e, fmt.format(e.dateStart)) { onOpenEvent(e.id) }
                        }
                    }
                }
            }
        }

        if (onCreateEvent != null && state.activeTab == 0) {
            FloatingActionButton(
                onClick = onCreateEvent,
                containerColor = PathfinderBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.Add, "Создать событие")
            }
        }
    }
}

@Composable
private fun ArchivedEventCard(
    event: EventEntity,
    dateStr: String,
    onRestore: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                event.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                dateStr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (event.archivedAt != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "В архиве с: ${SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(event.archivedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Восстановить")
            }
        }
    }
}

@Composable
private fun EventCard(event: EventEntity, dateStr: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                dateStr,
                style = MaterialTheme.typography.bodyLarge,
                color = PathfinderBlue
            )
            if (event.location.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    event.location,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
