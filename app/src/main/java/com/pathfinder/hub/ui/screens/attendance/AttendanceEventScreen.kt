package com.pathfinder.hub.ui.screens.attendance

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceEventScreen(
    onBack: () -> Unit,
    viewModel: AttendanceEventViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Посещаемость") },
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
            state.event == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text(state.errorMessage ?: "Событие не найдено", color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val fmt = SimpleDateFormat("d MMMM, HH:mm", Locale.forLanguageTag("ru"))
                    Text(state.event!!.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        fmt.format(state.event!!.dateStart),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    val present = state.rows.count { it.status == "attended" }
                    val absent = state.rows.count { it.status == "missed" }
                    Text(
                        "Было: $present  ·  Не было: $absent  ·  Всего: ${state.rows.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PathfinderBlue
                    )
                    Spacer(Modifier.height(12.dp))
                }

                state.errorMessage?.let { msg ->
                    item {
                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )) {
                            Text(msg, Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                if (state.rows.isEmpty()) {
                    item {
                        Text(
                            "Нет участников",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(state.rows, key = { it.userId }) { row ->
                        RowCard(row = row, onMark = { status -> viewModel.mark(row.userId, status) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RowCard(row: AttendanceRowUi, onMark: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = { Text(statusLabel(row.status)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = statusColor(row.status)
                    )
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = { onMark("attended") }, enabled = row.status != "attended") {
                    Text("Был")
                }
                TextButton(onClick = { onMark("missed") }, enabled = row.status != "missed") {
                    Text("Не был")
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "invited" -> "Приглашён"
    "accepted" -> "Принял"
    "declined" -> "Отказался"
    "attended" -> "Был"
    "missed" -> "Не был"
    else -> status
}

@Composable
private fun statusColor(status: String) = when (status) {
    "attended" -> MaterialTheme.colorScheme.primaryContainer
    "missed" -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}
