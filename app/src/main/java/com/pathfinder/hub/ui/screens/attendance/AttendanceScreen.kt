package com.pathfinder.hub.ui.screens.attendance

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал посещаемости") },
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
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.errorMessage != null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
            state.events.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Событий нет", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "За последние 30 дней не было событий клуба",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.events, key = { it.id }) { event ->
                    EventCard(event = event, onClick = { onOpenEvent(event.id) })
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: EventEntity, onClick: () -> Unit) {
    val fmt = SimpleDateFormat("d MMMM, HH:mm", Locale.forLanguageTag("ru"))
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Event, null, tint = PathfinderBlue)
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    fmt.format(event.dateStart),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
