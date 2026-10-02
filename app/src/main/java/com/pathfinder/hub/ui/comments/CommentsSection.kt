package com.pathfinder.hub.ui.comments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pathfinder.hub.ui.storage.FileUploadResult
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CommentsSection(
    targetType: String,
    targetId: String,
    modifier: Modifier = Modifier,
    pendingAttachment: FileUploadResult? = null,
    onAttachmentConsumed: () -> Unit = {},
    viewModel: CommentsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(targetType, targetId) {
        viewModel.init(targetType, targetId)
    }

    // ✅ Как только пришёл FileUploadResult — публикуем комментарий с вложением
    LaunchedEffect(pendingAttachment) {
        pendingAttachment?.let { result ->
            viewModel.attachFile(result)
            onAttachmentConsumed()
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
                modifier = Modifier.heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.comments, key = { it.id }) { comment ->
                    CommentBubble(comment)
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
                    Icon(Icons.Default.Send, "Отправить", tint = PathfinderBlue)
                }
            }
        }
    }
}

@Composable
private fun CommentBubble(comment: CommentUiModel) {
    val dateFormat = SimpleDateFormat("HH:mm, d MMM", Locale("ru"))

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
                    // ✅ Рендер вложений
                    comment.attachments.forEach { url ->
                        Spacer(Modifier.height(8.dp))
                        AttachmentPreview(url = url)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(url: String) {
    val lower = url.lowercase()
    val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".gif") ||
            lower.endsWith(".webp")

    if (isImage) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { /* TODO: открыть полноэкранный просмотр */ }
        )
    } else {
        // PDF / прочее — показываем иконку и имя файла
        val fileName = url.substringAfterLast('/')
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { /* TODO: открыть файл */ }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = PathfinderBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}