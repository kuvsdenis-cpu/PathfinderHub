package com.pathfinder.hub.data.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.pathfinder.hub.data.local.entity.reports.ReportEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Генератор PDF-отчёта о деятельности клуба.
 *
 * Использует android.graphics.pdf.PdfDocument — без внешних библиотек.
 *
 * Размер страницы: A4 в точках (595 x 842 px, 72 dpi).
 * Шрифт — Paint с размером, кратно 10-14 pt.
 */
@Singleton
class ReportPdfGenerator @Inject constructor() {

    companion object {
        private const val A4_WIDTH = 595
        private const val A4_HEIGHT = 842
        private const val MARGIN = 40
        private const val TEXT_SIZE_TITLE = 18f
        private const val TEXT_SIZE_HEADING = 13f
        private const val TEXT_SIZE_BODY = 11f
        private const val TEXT_SIZE_SMALL = 9f
        private const val LINE_HEIGHT_BODY = 16f
    }

    /**
     * Генерирует PDF-файл. Возвращает File.
     */
    fun generate(context: Context, report: ReportEntity, data: ReportData): File {
        val document = PdfDocument()
        var pageNumber = 1

        var page = document.startPage(
            PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
        )
        var canvas = page.canvas
        var y = MARGIN.toFloat()

        val titlePaint = TextPaint().apply {
            textSize = TEXT_SIZE_TITLE
            color = Color.BLACK
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headingPaint = TextPaint().apply {
            textSize = TEXT_SIZE_HEADING
            color = Color.BLACK
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            textSize = TEXT_SIZE_BODY
            color = Color.DKGRAY
            isAntiAlias = true
        }
        val smallPaint = TextPaint().apply {
            textSize = TEXT_SIZE_SMALL
            color = Color.GRAY
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
        }

        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru"))
        val fmtTime = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru"))

        // ---------- Заголовок ----------
        canvas.drawText("ОТЧЁТ О ДЕЯТЕЛЬНОСТИ КЛУБА", MARGIN.toFloat(), y, titlePaint)
        y += 24f
        canvas.drawText(data.clubName, MARGIN.toFloat(), y, headingPaint)
        y += 20f
        canvas.drawText("Период: ${fmt.format(report.periodStart)} — ${fmt.format(report.periodEnd)}", MARGIN.toFloat(), y, bodyPaint)
        y += LINE_HEIGHT_BODY
        canvas.drawText("Сгенерирован: ${fmtTime.format(data.generatedAt)}", MARGIN.toFloat(), y, bodyPaint)
        y += LINE_HEIGHT_BODY
        canvas.drawText("Автор: ${data.generatedByName}", MARGIN.toFloat(), y, bodyPaint)
        y += 20f
        canvas.drawLine(MARGIN.toFloat(), y, (A4_WIDTH - MARGIN).toFloat(), y, linePaint)
        y += 20f

        // ---------- 1. Общая статистика ----------
        canvas.drawText("1. ОБЩАЯ СТАТИСТИКА", MARGIN.toFloat(), y, headingPaint)
        y += 18f
        canvas.drawText("Всего участников: ${data.totalMembers}", MARGIN.toFloat(), y, bodyPaint)
        y += LINE_HEIGHT_BODY
        canvas.drawText("Следопытов: ${data.teens}", MARGIN.toFloat(), y, bodyPaint)
        y += LINE_HEIGHT_BODY
        canvas.drawText("Наставников: ${data.instructors}", MARGIN.toFloat(), y, bodyPaint)
        y += LINE_HEIGHT_BODY
        canvas.drawText("Родителей: ${data.parents}", MARGIN.toFloat(), y, bodyPaint)
        y += LINE_HEIGHT_BODY
        canvas.drawText("Секретарей: ${data.secretaries}", MARGIN.toFloat(), y, bodyPaint)
        y += 24f

        // ---------- 2. Прогресс по ступеням ----------
        canvas.drawText("2. ПРОГРЕСС ПО СТУПЕНЯМ", MARGIN.toFloat(), y, headingPaint)
        y += 18f
        canvas.drawText("Пройдено ступеней за период: ${data.levelsCompleted}", MARGIN.toFloat(), y, bodyPaint)
        y += LINE_HEIGHT_BODY
        canvas.drawText("Одобрено требований: ${data.levelRequirementsApproved}", MARGIN.toFloat(), y, bodyPaint)
        y += LINE_HEIGHT_BODY
        canvas.drawText("Ожидают проверки: ${data.levelRequirementsPending}", MARGIN.toFloat(), y, bodyPaint)
        y += 24f

        // ---------- 3. События ----------
        canvas.drawText("3. ПРОВЕДЁННЫЕ МЕРОПРИЯТИЯ", MARGIN.toFloat(), y, headingPaint)
        y += 18f
        if (data.events.isEmpty()) {
            canvas.drawText("Нет событий за период", MARGIN.toFloat(), y, bodyPaint)
            y += LINE_HEIGHT_BODY
        } else {
            data.events.forEach { e ->
                if (y > A4_HEIGHT - MARGIN - 100) {
                    // перенос на новую страницу
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(
                        PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
                    )
                    canvas = page.canvas
                    y = MARGIN.toFloat()
                }
                canvas.drawText("• ${fmt.format(e.dateStart)} — ${e.title}", MARGIN.toFloat(), y, bodyPaint)
                y += LINE_HEIGHT_BODY
                canvas.drawText(
                    "    ${e.location.ifBlank { "—" }} · согласий: ${e.consentsApproved} · задач: ${e.tasksAssigned}",
                    MARGIN.toFloat(), y, smallPaint
                )
                y += LINE_HEIGHT_BODY
            }
        }
        y += 12f

        // ---------- 4. QR-квесты ----------
        if (y > A4_HEIGHT - MARGIN - 100) {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(
                PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
            )
            canvas = page.canvas
            y = MARGIN.toFloat()
        }
        canvas.drawText("4. QR-КВЕСТЫ", MARGIN.toFloat(), y, headingPaint)
        y += 18f
        if (data.quests.isEmpty()) {
            canvas.drawText("Нет активных квестов за период", MARGIN.toFloat(), y, bodyPaint)
            y += LINE_HEIGHT_BODY
        } else {
            data.quests.forEach { q ->
                if (y > A4_HEIGHT - MARGIN - 80) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(
                        PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
                    )
                    canvas = page.canvas
                    y = MARGIN.toFloat()
                }
                canvas.drawText("• ${q.title}", MARGIN.toFloat(), y, bodyPaint)
                y += LINE_HEIGHT_BODY
                canvas.drawText(
                    "    станций: ${q.totalSteps} · начали: ${q.started} · завершили: ${q.completed}",
                    MARGIN.toFloat(), y, smallPaint
                )
                y += LINE_HEIGHT_BODY
            }
        }
        y += 12f

        // ---------- 5. Специализации ----------
        if (y > A4_HEIGHT - MARGIN - 100) {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(
                PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
            )
            canvas = page.canvas
            y = MARGIN.toFloat()
        }
        canvas.drawText("5. СПЕЦИАЛИЗАЦИИ", MARGIN.toFloat(), y, headingPaint)
        y += 18f
        canvas.drawText("Завершено специализаций: ${data.honorsCompleted}", MARGIN.toFloat(), y, bodyPaint)
        y += LINE_HEIGHT_BODY
        canvas.drawText("Одобрено требований: ${data.honorRequirementsApproved}", MARGIN.toFloat(), y, bodyPaint)
        y += LINE_HEIGHT_BODY
        canvas.drawText("Ожидают проверки: ${data.honorRequirementsPending}", MARGIN.toFloat(), y, bodyPaint)

        document.finishPage(page)

        // ---------- Сохранение ----------
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "report_${report.id}.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }
}
