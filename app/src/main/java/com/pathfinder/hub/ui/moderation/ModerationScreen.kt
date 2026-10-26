package com.pathfinder.hub.ui.moderation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.screens.home.director.ClubContextViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderRed
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationScreen(
    onBack: () -> Unit,
    viewModel: ModerationScreenViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Модерация") },
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
        ModerationContent(
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
            clubContextViewModel = clubContextViewModel,
        )
    }
}

@Composable
fun ModerationContent(
    modifier: Modifier = Modifier,
    viewModel: ModerationScreenViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clubState by clubContextViewModel.state.collectAsState()
    val dateFormat = SimpleDateFormat("HH:mm, d MMM", Locale.forLanguageTag("ru"))

    Column(modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.errorMessage != null && state.pendingComments.isEmpty() -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            state.pendingComments.isEmpty() -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = PathfinderGreen
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Все комментарии проверены",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Новых сообщений на модерации нет",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
                val grouped = remember(state.pendingComments) {
                    state.pendingComments.groupBy {
                        Triple(it.comment.authorId, it.comment.targetType, it.comment.targetId)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    grouped.forEach { (key, groupItems) ->
                        val (_, _, _) = key
                        val first = groupItems.first()
                        val groupTitle = "${first.authorName} · ${first.targetLabel}"
                        val groupKey = "g_${key.first}_${key.second}_${key.third}"
                        val defaultExpanded = groupItems.size <= 3
                        val isExpanded = expandedGroups[groupKey] ?: defaultExpanded
                        item(key = groupKey) {
                            ModerationGroupHeader(
                                title = groupTitle,
                                count = groupItems.size,
                                expanded = isExpanded,
                                onToggle = { expandedGroups[groupKey] = !isExpanded },
                            )
                        }
                        if (isExpanded) {
                            items(groupItems, key = { it.comment.id }) { item ->
                                ModerationCard(
                                    item = item,
                                    dateFormat = dateFormat,
                                    isProcessing = state.isProcessing,
                                    canArchive = state.canArchive,
                                    onApprove = { viewModel.approveComment(item.comment.id) },
                                    onHide = { viewModel.hideComment(item.comment.id) },
                                    onReject = { viewModel.showRejectDialog(item.comment.id) },
                                    onArchive = { viewModel.archiveComment(item.comment.id) },
                                    onDelete = { viewModel.showDeleteDialog(item.comment.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Удалить комментарий?") },
            text = {
                Text(
                    "Комментарий будет скрыт из чата и физически удалён через 90 дней. " +
                    "До этого момента его можно восстановить."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = PathfinderRed)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (state.showRejectDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideRejectDialog() },
            title = { Text("Отклонить комментарий") },
            text = {
                Column {
                    Text("Укажите причину отклонения:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.rejectReason,
                        onValueChange = viewModel::onRejectReasonChange,
                        label = { Text("Причина") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.rejectComment() },
                    enabled = state.rejectReason.isNotBlank()
                ) {
                    Text("Отклонить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRejectDialog() }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun ModerationGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = PathfinderBlue)
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            AssistChip(onClick = {}, label = { Text("$count") })
            Spacer(Modifier.width(4.dp))
            Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ModerationCard(
    item: CommentToModerate,
    dateFormat: SimpleDateFormat,
    isProcessing: Boolean,
    canArchive: Boolean,
    onApprove: () -> Unit,
    onHide: () -> Unit,
    onReject: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor = when (item.comment.status) {
        "pending_review" -> MaterialTheme.colorScheme.tertiary
        "published" -> PathfinderBlue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Report,
                    null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.targetLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = dateFormat.format(item.comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = item.authorName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = item.comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApprove,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PathfinderGreen)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Одобрить")
                }

                OutlinedButton(
                    onClick = onHide,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Скрыть")
                }

                OutlinedButton(
                    onClick = onReject,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PathfinderRed)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Отклонить")
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDelete,
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PathfinderRed)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Удалить (90 дней)")
            }

            if (canArchive) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onArchive,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("В архив")
                }
            }
        }
    }
}
