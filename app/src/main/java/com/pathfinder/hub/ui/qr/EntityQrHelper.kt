package com.pathfinder.hub.ui.qr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/**
 * Универсальный QR-генератор для событий, задач, станций.
 *
 * Payload формата "PFHUB:<TYPE>:<id>".
 */
object EntityQrHelper {

    private const val AUTHORITY = "com.pathfinder.hub.fileprovider"
    private const val TAG = "EntityQrHelper"

    fun eventPayload(eventId: String): String = "PFHUB:EVENT:$eventId"
    fun taskPayload(taskId: String): String = "PFHUB:TASK:$taskId"
    fun stationPayload(stationId: String): String = "PFHUB:STATION:$stationId"

    fun generatePngForPayload(
        context: Context,
        payload: String,
        filename: String,
        sizePx: Int = 800,
    ): File {
        val dir = File(context.cacheDir, "qr_codes").apply { mkdirs() }
        val file = File(dir, filename)

        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    fun share(context: Context, title: String, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться QR"))
        } catch (e: Exception) {
            Log.e(TAG, "share failed", e)
        }
    }

    fun open(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Открыть QR"))
        } catch (e: Exception) {
            Log.e(TAG, "open failed", e)
        }
    }
}
