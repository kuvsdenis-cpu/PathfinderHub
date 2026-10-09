package com.pathfinder.hub.ui.share

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Хелпер для ACTION_SEND PNG-карточки через FileProvider.
 */
object ShareCardHelper {

    private const val TAG = "ShareCardHelper"

    fun share(context: Context, file: File, caption: String = "") {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                ShareCardGenerator.authority(),
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться"))
        } catch (e: Exception) {
            Log.e(TAG, "share failed", e)
        }
    }
}
