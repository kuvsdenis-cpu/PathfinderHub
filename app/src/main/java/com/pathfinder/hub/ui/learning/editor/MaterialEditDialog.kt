package com.pathfinder.hub.ui.learning.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity

@Composable
fun MaterialEditDialog(
    existing: RequirementMaterialEntity?,
    uploadedUrl: String? = null,
    isUploading: Boolean = false,
    onDismiss: () -> Unit,
    onUploadFile: (Uri, String) -> Unit = { _, _ -> },
    onUploadedUrlConsumed: () -> Unit = {},
    onSave: (type: String, title: String, content: String) -> Unit,
) {
    var type by remember { mutableStateOf(existing?.type ?: "text") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }

    // Подхватываем загруженный URL
    LaunchedEffect(uploadedUrl) {
        if (!uploadedUrl.isNullOrBlank()) {
            content = uploadedUrl
            if (type !in listOf("file", "image")) type = "file"
            onUploadedUrlConsumed()
        }
    }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onUploadFile(it, title) } }

    val types = listOf("text", "link", "file", "image")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новый материал" else "Редактировать материал") },
        text = {
            Column {
                Text("Тип", style = MaterialTheme.typography.labelMedium)
                Row {
                    types.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Заголовок") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = {
                        Text(
                            when (type) {
                                "link" -> "URL"
                                "image" -> "URL картинки"
                                "file" -> "URL файла"
                                else -> "Текст"
                            }
                        )
                    },
                    minLines = if (type == "text") 4 else 1,
                    modifier = Modifier.fillMaxWidth()
                )

                if (type == "file" || type == "image") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        enabled = !isUploading,
                        onClick = {
                            pickFile.launch(if (type == "image") "image/*" else "*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (content.isBlank())
                                Icons.Default.CloudUpload
                            else
                                Icons.Default.Refresh,
                            contentDescription = null
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(
                            when {
                                isUploading -> "Загрузка…"
                                content.isBlank() -> " Загрузить файл"
                                else -> " Заменить файл"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && content.isNotBlank(),
                onClick = { onSave(type, title, content) }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
