package com.pathfinder.hub.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.pathfinder.hub.ui.theme.PathfinderBlue

/**
 * Универсальное превью вложения по URL.
 *  - Изображения (jpg/jpeg/png/gif/webp/bmp) → Coil
 *  - PDF / Word / Excel / PPT / архивы / видео / аудио → карточка с иконкой
 *
 * Работает с pre-signed URL (учитывает query-параметры при определении расширения).
 * Используется в чате, требованиях ступеней и специализаций.
 */
@Composable
fun AttachmentPreview(
    url: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    // Отрезаем query-параметры (?X-Amz-...) для корректного определения расширения
    val baseUrl = url.substringBefore("?")
    val ext = baseUrl.substringAfterLast('.', "").lowercase()

    if (ext in IMAGE_EXTENSIONS) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url) // передаём полный URL с подписью
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color.LightGray),
            error = ColorPainter(Color.Red),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        )
    } else {
        AttachmentIconCard(
            url = url,
            baseUrl = baseUrl,
            ext = ext,
            onClick = onClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun AttachmentIconCard(
    url: String,
    baseUrl: String,
    ext: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val (icon, label) = iconFor(ext)
    val fileName = baseUrl.substringAfterLast('/').take(80).ifBlank { "файл" }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PathfinderBlue,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif",
)

private fun iconFor(ext: String): Pair<ImageVector, String> = when (ext) {
    // Видео
    "mp4", "mov", "avi", "mkv", "webm", "3gp", "m4v" ->
        Icons.Default.PlayCircle to "Видео"

    // Аудио
    "mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "amr" ->
        Icons.Default.MusicNote to "Аудио"

    // PDF
    "pdf" -> Icons.Default.PictureAsPdf to "PDF"

    // Word
    "doc", "docx", "odt", "rtf" ->
        Icons.Default.Description to "Документ Word"

    // Excel / таблицы
    "xls", "xlsx", "ods", "csv" ->
        Icons.Default.GridOn to "Таблица"

    // PowerPoint
    "ppt", "pptx", "odp" ->
        Icons.Default.Slideshow to "Презентация"

    // Архивы
    "zip", "rar", "7z" ->
        Icons.Default.FolderZip to "Архив"

    // Текст / JSON / XML / EPUB
    "txt", "json", "xml", "epub" ->
        Icons.Default.Article to "Текст"

    else -> Icons.Default.InsertDriveFile to "Файл"
}