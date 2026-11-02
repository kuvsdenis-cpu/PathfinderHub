package com.pathfinder.hub.ui.screens.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBookScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateBookViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.uploadFile(uri, context)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая книга") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (!state.canCreate && !state.isSaving && !state.saved) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Нет прав на создание книги",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Название *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.author,
                onValueChange = viewModel::onAuthorChange,
                label = { Text("Автор *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            OutlinedTextField(
                value = state.externalUrl,
                onValueChange = viewModel::onExternalUrlChange,
                label = { Text("Ссылка на внешний ресурс") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.contentPath,
                onValueChange = viewModel::onContentPathChange,
                label = { Text("Путь к тексту в assets (например: books/kniga.json)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.coverUrl,
                onValueChange = viewModel::onCoverUrlChange,
                label = { Text("Обложка (URL)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    filePicker.launch("*/*")
                },
                enabled = !state.isUploading && !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.isUploading -> "Загрузка: ${(state.uploadProgress * 100).toInt()}%"
                        else -> "Загрузить файл (.txt / .fb2 / .pdf)"
                    }
                )
            }

            if (state.uploaded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "✓ Книга загружена и создана. Нажмите «Назад», чтобы вернуться к списку.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving && !state.uploaded && !state.isUploading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Сохранить")
                }
            }
        }
    }
}
