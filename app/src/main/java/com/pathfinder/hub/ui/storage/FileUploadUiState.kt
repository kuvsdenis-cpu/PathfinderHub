package com.pathfinder.hub.ui.storage

data class FileUploadUiState(
    val context: FileUploadContext = FileUploadContext.GENERIC,
    val contextId: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val uploadedUrl: String? = null,
    val errorMessage: String? = null
)