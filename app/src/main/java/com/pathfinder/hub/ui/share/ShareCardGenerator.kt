package com.pathfinder.hub.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Генератор PNG-карточек для шаринга. 1080x1080.
 * Фон — PathfinderBlue, белый текст, внизу шапка PATHFINDER HUB.
 *
 * Файл кладётся в cacheDir/share_cards/ (разрешено в file_paths.xml).
 */
object ShareCardGenerator {

    private const val SIZE = 1080
    private const val AUTHORITY = "com.pathfinder.hub.fileprovider"
    private const val BG = 0xFF1A3A6E.toInt() // PathfinderBlue
    private const val WHITE = Color.WHITE
    private const val WHITE_70 = 0xB3FFFFFF.toInt()
    private const val WHITE_50 = 0x80FFFFFF.toInt()

    fun generate(
        context: Context,
        emoji: String,
        title: String,
        subtitle: String,
        userName: String?,
        dateText: String? = null,
    ): File {
        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val file = File(dir, "share_${System.currentTimeMillis()}.png")

        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(BG)

        val centerX = SIZE / 2f

        // ---------- Верхний декор: тонкая полоса ----------
        val barPaint = Paint().apply { color = 0x33FFFFFF }
        canvas.drawRect(0f, 0f, SIZE.toFloat(), 8f, barPaint)

        // ---------- Emoji ----------
        if (emoji.isNotBlank()) {
            val emojiPaint = Paint().apply {
                isAntiAlias = true
                textSize = 200f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(emoji, centerX, 340f, emojiPaint)
        }

        // ---------- Заголовок ----------
        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 72f
            color = WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        drawWrappedText(canvas, title, centerX, 480f, titlePaint, SIZE - 160f, 80f)

        // ---------- Подзаголовок ----------
        val subPaint = Paint().apply {
            isAntiAlias = true
            textSize = 40f
            color = WHITE_70
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(subtitle, centerX, 620f, subPaint)

        // ---------- Имя ----------
        if (!userName.isNullOrBlank()) {
            val namePaint = Paint().apply {
                isAntiAlias = true
                textSize = 52f
                color = WHITE
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText(userName, centerX, 720f, namePaint)
        }

        // ---------- Дата ----------
        val dateStr = dateText
            ?: SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date())
        val datePaint = Paint().apply {
            isAntiAlias = true
            textSize = 34f
            color = WHITE_50
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(dateStr, centerX, 790f, datePaint)

        // ---------- Нижняя шапка ----------
        val footerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 42f
            color = WHITE
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.2f
            isFakeBoldText = true
        }
        canvas.drawText("PATHFINDER HUB", centerX, 980f, footerPaint)

        // ---------- Рамка ----------
        val framePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = WHITE_50
        }
        canvas.drawRect(40f, 40f, SIZE - 40f, SIZE - 40f, framePaint)

        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bmp.recycle()

        return file
    }

    /**
     * Разбивает длинный текст на строки, если не влезает.
     */
    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        cx: Float,
        startY: Float,
        paint: Paint,
        maxWidth: Float,
        lineHeight: Float,
    ) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val test = if (current.isEmpty()) w else current.toString() + " " + w
            if (paint.measureText(test) <= maxWidth) {
                if (current.isNotEmpty()) current.append(" ")
                current.append(w)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())

        var y = startY
        for (line in lines) {
            canvas.drawText(line, cx, y, paint)
            y += lineHeight
        }
    }

    fun authority(): String = AUTHORITY
}
