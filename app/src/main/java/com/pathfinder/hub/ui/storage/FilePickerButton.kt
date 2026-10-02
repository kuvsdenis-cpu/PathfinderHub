package com.pathfinder.hub.ui.storage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Composable
fun FilePickerButton(
    modifier: Modifier = Modifier,
    label: String = "Прикрепить файл",
    onFileUploaded: (String) -> Unit,
    viewModel: FileUploadViewModel
) {
    val state by viewModel.state.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFileSelected(uri)
        }
    }

    Column(modifier = modifier) {
        if (state.isUploading) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = PathfinderBlue
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Загрузка: ${state.fileName}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = PathfinderBlue
                    )
                }
            }
        } else if (state.uploadedUrl != null) {
            // ✅ ИЗМЕНЕНО: fileUrl → uploadedUrl
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Файл успешно загружен",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = { viewModel.reset() }) {
                    Text("Удалить")
                }
            }
            // Уведомляем родительский компонент о новой ссылке
            LaunchedEffect(state.uploadedUrl) {
                // ✅ ИЗМЕНЕНО: fileUrl → uploadedUrl
                state.uploadedUrl?.let { url ->
                    onFileUploaded(url)
                }
            }
        } else if (state.errorMessage != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Error,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = state.errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.reset() }) {
                    Text("Повторить")
                }
            }
        } else {
            OutlinedButton(
                onClick = { launcher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }
}