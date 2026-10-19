package com.pathfinder.hub.ui.screens.certificates

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Генератор PDF-сертификата через встроенный android.graphics.pdf.PdfDocument.
 *
 * Формат: A4 landscape (842 x 595 pt), рамка, шапка, крупный титул,
 * ФИО получателя, название ступени/специализации, дата, подпись.
 *
 * Файл пишется в cacheDir/certs/cert_<id>.pdf — этот путь разрешён
 * в res/xml/file_paths.xml (cache-path).
 */
object CertificatePdfGenerator {

    private const val PAGE_W = 842
    private const val PAGE_H = 595
    private const val AUTHORITY = "com.pathfinder.hub.fileprovider"

    fun generate(
        context: Context,
        certificateId: String,
        userName: String,
        title: String,        // "Сертификат о прохождении ступени"
        subject: String,      // "Следопыт" / "Первая помощь"
        issuedAt: Date,
    ): File {
        val dir = File(context.cacheDir, "certs").apply { mkdirs() }
        val file = File(dir, "cert_$certificateId.pdf")

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // ---------- Paints ----------
        val accent = Color.parseColor("#1A3A6E")
        val headerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 24f
            color = accent
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            letterSpacing = 0.15f
        }
        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 52f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 20f
            color = Color.DKGRAY
            textAlign = Paint.Align.CENTER
        }
        val subjectPaint = Paint().apply {
            isAntiAlias = true
            textSize = 44f
            color = accent
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val namePaint = Paint().apply {
            isAntiAlias = true
            textSize = 30f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val smallPaint = Paint().apply {
            isAntiAlias = true
            textSize = 14f
            color = Color.GRAY
            textAlign = Paint.Align.CENTER
        }
        val framePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = accent
        }
        val innerFramePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = accent
        }

        // ---------- Рамки ----------
        canvas.drawRect(20f, 20f, PAGE_W - 20f, PAGE_H - 20f, framePaint)
        canvas.drawRect(30f, 30f, PAGE_W - 30f, PAGE_H - 30f, innerFramePaint)

        val cx = PAGE_W / 2f

        // ---------- Содержимое ----------
        canvas.drawText("PATHFINDER HUB", cx, 90f, headerPaint)
        canvas.drawText("СЕРТИФИКАТ", cx, 180f, titlePaint)
        canvas.drawText(title, cx, 220f, subtitlePaint)

        canvas.drawText("«" + subject + "»", cx, 300f, subjectPaint)

        canvas.drawText("Выдан", cx, 355f, smallPaint)
        canvas.drawText(userName, cx, 395f, namePaint)

        val fmt = SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ru"))
        canvas.drawText("Дата выдачи: " + fmt.format(issuedAt), cx, 445f, smallPaint)

        canvas.drawText("Pathfinder Hub", cx, 525f, smallPaint)

        pdf.finishPage(page)

        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        return file
    }

    fun authority(): String = AUTHORITY
}
