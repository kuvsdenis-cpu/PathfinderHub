package com.pathfinder.hub.ui.qr.quests

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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageQuestsScreen(
    onBack: () -> Unit,
    onOpenQuest: (String) -> Unit,
    onCreateQuest: () -> Unit,
    viewModel: ManageQuestsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR-квесты") },
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
        ManageQuestsContent(
            onOpenQuest = onOpenQuest,
            onCreateQuest = onCreateQuest,
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
        )
    }
}

@Composable
fun ManageQuestsContent(
    onOpenQuest: (String) -> Unit,
    onCreateQuest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageQuestsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var deleteId by remember { mutableStateOf<String?>(null) }

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EmojiEvents, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Пока нет квестов", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Нажмите + чтобы создать первый",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.quest.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenQuest(item.quest.id) }
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents, null,
                                tint = PathfinderBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.quest.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (item.quest.description.isNotBlank()) {
                                    Text(
                                        item.quest.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("${item.stepCount} станций") }
                                    )
                                    if (item.quest.rewardPoints > 0) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("+${item.quest.rewardPoints}") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Star, null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            if (state.canArchive) {
                                IconButton(onClick = { viewModel.archiveQuest(item.quest.id) }) {
                                    Icon(
                                        Icons.Default.Archive, "В архив",
                                        tint = PathfinderBlue
                                    )
                                }
                            }
                            IconButton(onClick = { deleteId = item.quest.id }) {
                                Icon(
                                    Icons.Default.Delete, "Удалить",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateQuest,
            containerColor = PathfinderBlue,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, "Создать квест")
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Удалить квест?") },
            text = { Text("Все связи со станциями и прогресс участников будут потеряны.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQuest(id)
                        deleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}
