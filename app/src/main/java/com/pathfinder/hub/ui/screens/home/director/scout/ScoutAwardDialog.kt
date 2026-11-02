package com.pathfinder.hub.ui.screens.home.director.scout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.data.local.entity.learning.HonorEntity

@Composable
fun ScoutAwardDialog(
    allHonors: List<HonorEntity>,
    alreadyCompletedIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (honorId: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = allHonors
        .filter { h ->
            query.isBlank() || h.name.contains(query, ignoreCase = true)
        }
        .sortedBy { it.name }

    var selectedId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выдать специализацию") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск по названию") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filtered, key = { it.id }) { h ->
                        val isCompleted = h.id in alreadyCompletedIds
                        FilterChip(
                            selected = selectedId == h.id,
                            enabled = !isCompleted,
                            onClick = { selectedId = h.id },
                            label = {
                                Text(
                                    h.name + if (isCompleted) " (уже выдана)" else ""
                                )
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedId?.let { onConfirm(it) } },
                enabled = selectedId != null,
            ) { Text("Выдать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
