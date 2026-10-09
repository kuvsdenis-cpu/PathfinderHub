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
 * Генератор QR-PNG для станции.
 * Payload: "PFHUB:STATION:<stationId>".
 * Файл пишется в cacheDir/qr_codes/station_<id>.png.
 */
object StationQrHelper {

    private const val AUTHORITY = "com.pathfinder.hub.fileprovider"
    private const val TAG = "StationQrHelper"

    fun payload(stationId: String): String = "PFHUB:STATION:$stationId"

    /**
     * Генерирует PNG с QR-кодом. Возвращает File.
     */
    fun generatePng(context: Context, stationId: String, sizePx: Int = 800): File {
        val dir = File(context.cacheDir, "qr_codes").apply { mkdirs() }
        val file = File(dir, "station_$stationId.png")

        val text = payload(stationId)
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

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

    /**
     * Поделиться PNG через ACTION_SEND.
     */
    fun share(context: Context, stationName: String, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "QR-станция: $stationName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться QR"))
        } catch (e: Exception) {
            Log.e(TAG, "share failed", e)
        }
    }

    /**
     * Открыть PNG в системном просмотрщике.
     */
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
