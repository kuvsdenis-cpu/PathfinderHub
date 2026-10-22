package com.pathfinder.hub.ui.chat.dm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.common.AttachmentPreview
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DMChatScreen(
    onBack: () -> Unit,
    viewModel: DMChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var firstScrollDone by remember { mutableStateOf(false) }

    // При первом показе — instant scroll (не мигаем первой страницей).
    // При новых сообщениях — animate.
    LaunchedEffect(state.messages.size, state.isLoading) {
        if (!state.isLoading && state.messages.isNotEmpty()) {
            val target = state.messages.size - 1
            if (!firstScrollDone) {
                listState.scrollToItem(target)
                firstScrollDone = true
            } else {
                listState.animateScrollToItem(target)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.otherName,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
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
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(Modifier.weight(1f), Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null && state.messages.isEmpty() -> Box(
                    Modifier.weight(1f), Alignment.Center,
                ) {
                    Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(
                            msg = msg,
                            isOwn = msg.authorId == state.currentUserId,
                            onLongPress = if (msg.authorId == state.currentUserId)
                                ({ viewModel.startEdit(msg) }) else null,
                        )
                    }
                }
            }

            HorizontalDivider()

            if (state.editingMessageId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.editDraft,
                        onValueChange = viewModel::onEditDraftChange,
                        placeholder = { Text("Редактирование...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = viewModel::cancelEdit) { Text("Отмена") }
                    TextButton(
                        onClick = viewModel::submitEdit,
                        enabled = state.editDraft.isNotBlank(),
                    ) { Text("Сохранить") }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = viewModel::onInputChange,
                        placeholder = { Text("Сообщение...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        enabled = !state.isSending,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = viewModel::send,
                        enabled = state.inputText.isNotBlank() && !state.isSending,
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить",
                                tint = PathfinderBlue)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: com.pathfinder.hub.domain.model.ChatMessage,
    isOwn: Boolean,
    onLongPress: (() -> Unit)? = null,
) {
    val dateFormat = SimpleDateFormat("HH:mm, d MMM", Locale.forLanguageTag("ru"))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isOwn) PathfinderBlue.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                ),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                msg.attachments.forEach { url ->
                    Spacer(Modifier.height(6.dp))
                    AttachmentPreview(url = url)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(dateFormat.format(msg.createdAt))
                        msg.editedAt?.let { append(" · изм.") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
