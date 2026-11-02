package com.pathfinder.hub.ui.info

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.repository.KnowledgeBaseRepository
import com.pathfinder.hub.data.repository.StorageRepository
import com.pathfinder.hub.ui.theme.PathfinderBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class InfoEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: KnowledgeBaseRepository,
    private val storageRepo: StorageRepository
) : ViewModel() {
    val categoryId: String = savedStateHandle.get<String>("categoryId") ?: ""
    val articleId: String? = savedStateHandle.get<String>("articleId")?.takeIf { it != "new" }

    var title by mutableStateOf("")
    var content by mutableStateOf("")
    var pdfUrl by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)
    var isUploading by mutableStateOf(false)
    var uploadProgress by mutableStateOf(0f)

    fun save(onSaved: () -> Unit) {
        if (title.isBlank()) return
        isSaving = true
        viewModelScope.launch {
            repo.saveArticle(categoryId, title, content, pdfUrl, articleId)
            isSaving = false
            onSaved()
        }
    }

    fun uploadPdf(uri: Uri, context: android.content.Context, title: String) {
        isUploading = true
        uploadProgress = 0f
        viewModelScope.launch {
            try {
                // Копируем выбранный файл во временную папку, чтобы получить File
                val file = withContext(Dispatchers.IO) {
                    val tempFile = File(context.cacheDir, "upload_${UUID.randomUUID()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempFile
                }

                val key = storageRepo.buildObjectKey("info_pdfs", title.ifBlank { "article" }, "pdf")
                val result = storageRepo.uploadFile(file, key) { progress ->
                    uploadProgress = progress
                }
                
                file.delete() // Удаляем временный файл

                result.fold(
                    onSuccess = { _ ->
                        // uploadFile возвращает presigned URL с TTL 7 дней.
                        // Он протухнет у ВСЕХ пользователей. Сохраняем постоянный
                        // публичный URL по ключу (требует public-read на бакете).
                        pdfUrl = storageRepo.getPublicUrl(key)
                    },
                    onFailure = { e ->
                        // TODO: показать ошибку пользователю
                    }
                )
            } catch (e: Exception) {
                // Обработка ошибки
            } finally {
                isUploading = false
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (articleId == null) return
        viewModelScope.launch {
            repo.deleteArticle(articleId)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoEditorScreen(
    onBack: () -> Unit,
    viewModel: InfoEditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.uploadPdf(uri, context, viewModel.title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.articleId == null) "Новая статья" else "Редактировать") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSaved = onBack) },
                        enabled = viewModel.title.isNotBlank() && !viewModel.isSaving
                    ) { Text("Сохранить") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = { viewModel.title = it },
                label = { Text("Заголовок") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = viewModel.content,
                onValueChange = { viewModel.content = it },
                label = { Text("Текст статьи") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                maxLines = 20
            )
            Spacer(Modifier.height(16.dp))
            
            Button(
                onClick = { picker.launch("application/pdf") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isUploading
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        viewModel.isUploading -> "Загрузка PDF: ${(viewModel.uploadProgress * 100).toInt()}%"
                        viewModel.pdfUrl != null -> "PDF прикреплён"
                        else -> "Прикрепить PDF"
                    }
                )
            }
            if (viewModel.pdfUrl != null) {
                TextButton(onClick = { viewModel.pdfUrl = null }) { Text("Удалить PDF") }
            }
            
            Spacer(Modifier.height(32.dp))
            if (viewModel.articleId != null) {
                OutlinedButton(
                    onClick = { viewModel.delete(onDeleted = onBack) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить статью") }
            }
        }
    }
}
