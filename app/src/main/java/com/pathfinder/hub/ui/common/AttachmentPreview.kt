package com.pathfinder.hub.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pathfinder.hub.ui.learning.FileIntentUtil
import com.pathfinder.hub.ui.theme.PathfinderBlue
import kotlinx.coroutines.launch

/**
 * Превью вложения по URL.
 *  - Картинки → Coil (при 403 — refresh + retry)
 *  - Остальное → карточка с иконкой (клик — refresh + открыть)
 *
 * [refreshUrl] — перегенерировать presigned URL, если он протух (TTL 7 дней).
 * Передавать из ViewModel (StorageRepository.refreshPresignedUrl).
 */
@Composable
fun AttachmentPreview(
    url: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    refreshUrl: (suspend (String) -> String?)? = null,
) {
    val baseUrl = url.substringBefore("?")
    val ext = baseUrl.substringAfterLast('.', "").lowercase()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentUrl by remember(url) { mutableStateOf(url) }
    var refreshing by remember(url) { mutableStateOf(false) }
    var retried by remember(url) { mutableIntStateOf(0) }

    fun tryRefresh(onDone: (String?) -> Unit) {
        if (refreshUrl == null || refreshing) { onDone(null); return }
        refreshing = true
        scope.launch {
            val fresh = try { refreshUrl(currentUrl) } catch (_: Exception) { null }
            refreshing = false
            if (fresh != null && fresh != currentUrl) {
                currentUrl = fresh
                retried++
            }
            onDone(fresh)
        }
    }

    if (ext in IMAGE_EXTENSIONS) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Color.LightGray),
                error = ColorPainter(Color.Red),
                onError = {
                    if (retried < 2) tryRefresh { _ -> }
                },
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            )
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    } else {
        AttachmentIconCard(
            url = currentUrl,
            baseUrl = baseUrl,
            ext = ext,
            refreshing = refreshing,
            onClick = {
                if (onClick != null) { onClick(); return@AttachmentIconCard }
                if (refreshUrl != null) {
                    refreshing = true
                    scope.launch {
                        val fresh = try { refreshUrl(currentUrl) } catch (_: Exception) { null }
                        refreshing = false
                        if (fresh != null) currentUrl = fresh
                        FileIntentUtil.openFile(context, currentUrl)
                    }
                } else {
                    FileIntentUtil.openFile(context, currentUrl)
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun AttachmentIconCard(
    url: String,
    baseUrl: String,
    ext: String,
    refreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, label) = iconFor(ext)
    val fileName = baseUrl.substringAfterLast('/').take(80).ifBlank { "файл" }

    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = PathfinderBlue, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

private fun iconFor(ext: String): Pair<ImageVector, String> = when (ext) {
    "mp4", "mov", "avi", "mkv", "webm", "3gp", "m4v" -> Icons.Default.PlayCircle to "Видео"
    "mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "amr" -> Icons.Default.MusicNote to "Аудио"
    "pdf" -> Icons.Default.PictureAsPdf to "PDF"
    "doc", "docx", "odt", "rtf" -> Icons.Default.Description to "Документ Word"
    "xls", "xlsx", "ods", "csv" -> Icons.Default.GridOn to "Таблица"
    "ppt", "pptx", "odp" -> Icons.Default.Slideshow to "Презентация"
    "zip", "rar", "7z" -> Icons.Default.FolderZip to "Архив"
    "txt", "json", "xml", "epub" -> Icons.Default.Article to "Текст"
    else -> Icons.Default.InsertDriveFile to "Файл"
}
