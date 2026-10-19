package com.pathfinder.hub.ui.storage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileUploadScreen(
    onBack: () -> Unit,
    onSuccess: (FileUploadResult) -> Unit,
    viewModel: FileUploadViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onFileSelected(uri)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.uploadedUrl) {
        if (state.uploadedUrl != null) {
            val result = viewModel.buildResult()
            if (result != null) onSuccess(result)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.context.screenTitle) },
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
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = PathfinderBlue
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = state.context.screenTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Разрешено: ${allowedTypesLabel(state.context)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            if (state.fileName.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = state.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatSize(state.sizeBytes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.mimeType,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.isUploading) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = PathfinderBlue
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        if (state.uploadedUrl != null) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = PathfinderGreen
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Загружено",
                                    color = PathfinderGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            Spacer(Modifier.weight(1f))

            if (state.uploadedUrl == null) {
                Button(
                    onClick = {
                        val mime = resolvePickerMime(state.context.allowedMimeTypes)
                        filePicker.launch(mime)
                    },
                    enabled = !state.isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (state.isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.fileName.isBlank()) "Выбрать файл" else "Выбрать другой")
                    }
                }
            }
        }
    }
}

private fun resolvePickerMime(allowed: String): String {
    val trimmed = allowed.trim()
    if (trimmed.isBlank()) return "*/*"
    if (trimmed.contains(',')) return "*/*"
    return when (trimmed) {
        "image/*", "video/*", "audio/*", "application/pdf" -> trimmed
        else -> "*/*"
    }
}

private fun allowedTypesLabel(ctx: FileUploadContext): String {
    val types = ctx.allowedMimeTypes
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

    if (types.isEmpty() || types.contains("*/*")) return "Любые файлы"

    val labels = mutableListOf<String>()
    if (types.any { it == "image/*" || it.startsWith("image/") }) labels += "Изображения"
    if (types.any { it == "video/*" || it.startsWith("video/") }) labels += "Видео"
    if (types.any { it == "audio/*" || it.startsWith("audio/") }) labels += "Аудио"
    if (types.any { it.startsWith("application/pdf") }) labels += "PDF"
    if (types.any { it.contains("word") || it == "application/msword" || it == "application/rtf" }) labels += "Документы Word"
    if (types.any { it.contains("excel") || it.contains("spreadsheet") || it == "text/csv" }) labels += "Таблицы"
    if (types.any { it.contains("presentation") || it.contains("powerpoint") }) labels += "Презентации"
    if (types.any { it.contains("zip") || it.contains("rar") || it.contains("7z") }) labels += "Архивы"
    if (types.any { it == "text/plain" || it == "application/json" || it == "application/xml" }) labels += "Текст"

    return if (labels.isEmpty()) "Любые файлы" else labels.joinToString(", ")
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "неизвестно"
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} МБ"
}