package com.pathfinder.hub.ui.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.util.UUID
import javax.inject.Inject

data class FileUploadState(
    val fileName: String = "",
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val fileUrl: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class FileUploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FileUploadState())
    val state: StateFlow<FileUploadState> = _state.asStateFlow()

    fun onFileSelected(uri: Uri) {
        val fileName = getFileName(uri) ?: "unknown_file"
        _state.update { it.copy(fileName = fileName, isUploading = true, progress = 0.1f, errorMessage = null, fileUrl = null) }

        viewModelScope.launch {
            try {
                val tempFile = copyUriToTempFile(uri, fileName)
                _state.update { it.copy(progress = 0.5f) }

                val objectKey = "uploads/${UUID.randomUUID()}_$fileName"
                val result = storageRepository.uploadFile(tempFile, objectKey) { progress ->
                    _state.update { it.copy(progress = 0.5f + (progress * 0.5f)) }
                }

                result.onSuccess { url ->
                    _state.update { it.copy(isUploading = false, progress = 1f, fileUrl = url) }
                }.onFailure { error ->
                    _state.update { it.copy(isUploading = false, errorMessage = error.message ?: "Ошибка загрузки") }
                }

                // Очистка временного файла после загрузки
                tempFile.delete()
            } catch (e: Exception) {
                Log.e("FileUploadViewModel", "Ошибка обработки файла", e)
                _state.update { it.copy(isUploading = false, errorMessage = e.message ?: "Ошибка обработки файла") }
            }
        }
    }

    private fun copyUriToTempFile(uri: Uri, fileName: String): File {
        val tempFile = File(context.cacheDir, "upload_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Не удалось открыть файл")
        return tempFile
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { File(it).name }
        }
        return result
    }

    fun reset() {
        _state.update { FileUploadState() }
    }
}