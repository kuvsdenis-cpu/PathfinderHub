package com.pathfinder.hub.ui.comments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.common.AttachmentPreview
import com.pathfinder.hub.ui.storage.FileUploadResult
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CommentsSection(
    targetType: String,
    targetId: String,
    clubId: String,
    modifier: Modifier = Modifier,
    pendingAttachment: FileUploadResult? = null,
    onAttachmentConsumed: () -> Unit = {},
    viewModel: CommentsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var firstScrollDone by remember { mutableStateOf(false) }

    LaunchedEffect(targetType, targetId, clubId) {
        viewModel.init(targetType, targetId, clubId)
    }

    LaunchedEffect(pendingAttachment) {
        pendingAttachment?.let { result ->
            viewModel.attachFile(result)
            onAttachmentConsumed()
        }
    }

    // Скролл к последнему сообщению
    LaunchedEffect(state.comments.size, state.isLoading) {
        if (!state.isLoading && state.comments.isNotEmpty()) {
            val target = state.comments.size - 1
            if (!firstScrollDone) {
                listState.scrollToItem(target)
                firstScrollDone = true
            } else {
                listState.animateScrollToItem(target)
            }
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Обсуждение (${state.comments.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (state.comments.isEmpty()) {
            Text(
                text = "Пока нет комментариев. Будьте первым!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.comments, key = { it.id }) { comment ->
                    CommentBubble(
                        comment = comment,
                        refreshUrl = { url -> viewModel.refreshAttachmentUrl(url) },
                    )
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::onInputChange,
                placeholder = { Text("Написать комментарий...") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                enabled = !state.isSending
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = viewModel::sendComment,
                enabled = state.inputText.isNotBlank() && !state.isSending
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = PathfinderBlue)
                }
            }
        }
    }
}

@Composable
private fun CommentBubble(
    comment: CommentUiModel,
    refreshUrl: (suspend (String) -> String?)? = null,
) {
    val dateFormat = SimpleDateFormat("HH:mm, d MMM", Locale.forLanguageTag("ru"))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(36.dp).clip(CircleShape),
            color = if (comment.isOwn) PathfinderBlue else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = comment.authorInitials,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (comment.isOwn)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = dateFormat.format(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (comment.isOwn)
                    PathfinderBlue.copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = comment.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    comment.attachments.forEach { url ->
                        Spacer(Modifier.height(8.dp))
                        AttachmentPreview(url = url, refreshUrl = refreshUrl)
                    }
                }
            }
        }
    }
}
