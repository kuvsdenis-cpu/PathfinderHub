package com.pathfinder.hub.ui.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class FileUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val storageRepository: StorageRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val contextTypeArg: String = savedStateHandle.get<String>("contextType") ?: ""
    private val contextIdArg: String = savedStateHandle.get<String>("contextId") ?: ""

    private val uploadContext: FileUploadContext = FileUploadContext.fromString(contextTypeArg)

    private val _state = MutableStateFlow(
        FileUploadUiState(
            context = uploadContext,
            contextId = contextIdArg
        )
    )
    val state: StateFlow<FileUploadUiState> = _state.asStateFlow()

    fun onFileSelected(uri: Uri) {
        val userId = sessionManager.getUserId()
        if (userId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Нет активной сессии") }
            return
        }

        val fileName = queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val size = queryFileSize(uri)

        _state.update {
            it.copy(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = size,
                isUploading = true,
                progress = 0.05f,
                errorMessage = null,
                uploadedUrl = null
            )
        }

        viewModelScope.launch {
            try {
                val tempFile = copyUriToTempFile(uri, fileName)
                _state.update { it.copy(progress = 0.2f) }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val objectKey = buildString {
                    append(uploadContext.storagePrefix).append("/")
                    append(userId).append("/")
                    if (contextIdArg.isNotBlank()) {
                        append(contextIdArg).append("/")
                    }
                    append("${timestamp}_$safeName")
                }

                val result = storageRepository.uploadFile(tempFile, objectKey) { p ->
                    _state.update { it.copy(progress = 0.2f + p * 0.8f) }
                }

                tempFile.delete()

                result.onSuccess { url ->
                    _state.update {
                        it.copy(isUploading = false, progress = 1f, uploadedUrl = url)
                    }
                    Log.d(TAG, "✓ Uploaded: $url")
                }.onFailure { e ->
                    Log.e(TAG, "✗ Upload failed", e)
                    _state.update {
                        it.copy(
                            isUploading = false,
                            errorMessage = e.message ?: "Ошибка загрузки"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Processing error", e)
                _state.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = e.message ?: "Ошибка обработки файла"
                    )
                }
            }
        }
    }

    fun buildResult(): FileUploadResult? {
        val url = _state.value.uploadedUrl ?: return null
        return FileUploadResult(
            context = uploadContext,
            contextId = contextIdArg,
            fileUrl = url,
            fileName = _state.value.fileName,
            mimeType = _state.value.mimeType,
            sizeBytes = _state.value.sizeBytes
        )
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * Сбросить состояние загрузки.
     * Используется FilePickerButton для очистки после успешной загрузки.
     */
    fun reset() {
        _state.update {
            FileUploadUiState(
                context = uploadContext,
                contextId = contextIdArg
            )
        }
    }

    private fun copyUriToTempFile(uri: Uri, fileName: String): File {
        val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Не удалось открыть файл")
        return tempFile
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) return cursor.getString(idx)
                }
            }
        }
        return uri.path?.let { File(it).name }
    }

    private fun queryFileSize(uri: Uri): Long {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1 && !cursor.isNull(idx)) return cursor.getLong(idx)
                }
            }
        }
        return 0L
    }

    companion object {
        private const val TAG = "FileUploadVM"
    }
}