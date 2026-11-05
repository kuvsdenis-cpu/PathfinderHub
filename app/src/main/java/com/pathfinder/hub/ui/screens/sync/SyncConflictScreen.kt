package com.pathfinder.hub.ui.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.offline.SyncConflictEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConflictScreen(
    onBack: () -> Unit,
    viewModel: SyncConflictViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var pendingConflict by remember { mutableStateOf<SyncConflictEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Конфликты синхронизации") },
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
            state.conflicts.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Конфликтов нет", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Все данные синхронизированы",
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
                items(state.conflicts, key = { it.id }) { conflict ->
                    ConflictCard(conflict = conflict, onResolve = { pendingConflict = conflict })
                }
            }
        }
    }

    pendingConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { pendingConflict = null },
            title = { Text("Разрешить конфликт") },
            text = {
                Column {
                    Text("Тип: ${conflict.entityType}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Локальное значение:", fontWeight = FontWeight.SemiBold)
                    Text(conflict.localValue, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Серверное значение:", fontWeight = FontWeight.SemiBold)
                    Text(conflict.serverValue, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resolve(conflict.id, chooseLocal = true)
                    pendingConflict = null
                }) { Text("Взять локальное") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.resolve(conflict.id, chooseLocal = false)
                    pendingConflict = null
                }) { Text("Взять серверное") }
            }
        )
    }
}

@Composable
private fun ConflictCard(conflict: SyncConflictEntity, onResolve: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${conflict.entityType}: ${conflict.entityId.take(8)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Локально: ${conflict.localValue.take(80)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Сервер: ${conflict.serverValue.take(80)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onResolve) {
                Text("Разрешить", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
