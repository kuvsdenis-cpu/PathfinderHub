package com.pathfinder.hub.ui.storage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FileUploadResult(
    val context: FileUploadContext,
    val contextId: String,
    val fileUrl: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
) : Parcelable {
    companion object {
        const val SAVED_STATE_KEY = "file_upload_result"
    }
}