package com.pathfinder.hub.domain.usecase.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.pathfinder.hub.data.local.entity.reports.ReportEntity
import com.pathfinder.hub.data.repository.ReportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class GenerateReportUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        clubId: String?,
        level: String,
        periodStart: Date,
        periodEnd: Date,
        generatedBy: String
    ): Result<String> = try {
        val reportId = UUID.randomUUID().toString()
        val fileName = "report_${level}_${periodStart.time}.pdf"
        val file = File(context.filesDir, fileName)

        // 1. Реальная генерация PDF через Android PdfDocument (формат A4: 595 x 842 points)
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply {
            textSize = 14f
            color = Color.BLACK
        }
        val titlePaint = Paint().apply {
            textSize = 22f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val subtitlePaint = Paint().apply {
            textSize = 16f
            isFakeBoldText = true
            color = Color.DKGRAY
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
        var yPosition = 60f

        // Заголовок
        canvas.drawText("ОТЧЁТ: $level", 50f, yPosition, titlePaint)
        yPosition += 40f

        // Метаданные
        canvas.drawText("Период: ${dateFormat.format(periodStart)} – ${dateFormat.format(periodEnd)}", 50f, yPosition, paint)
        yPosition += 25f
        canvas.drawText("Дата генерации: ${dateFormat.format(Date())}", 50f, yPosition, paint)
        yPosition += 25f
        canvas.drawText("Сформировал: $generatedBy", 50f, yPosition, paint)
        yPosition += 50f

        // Разделительная линия (рисуем как текст или можно использовать canvas.drawLine)
        canvas.drawLine(50f, yPosition, 545f, yPosition, Paint().apply {
            strokeWidth = 2f; color = Color.LTGRAY
        })
        yPosition += 40f

        // Содержание (заглушки для будущей интеграции с реальными данными)
        canvas.drawText("СОДЕРЖАНИЕ ОТЧЁТА", 50f, yPosition, subtitlePaint)
        yPosition += 35f

        canvas.drawText("1. Посещаемость и активность клуба", 50f, yPosition, paint)
        yPosition += 25f
        canvas.drawText("   - Среднее количество участников: [данные]", 70f, yPosition, paint)
        yPosition += 35f

        canvas.drawText("2. Прогресс по ступеням и специализациям", 50f, yPosition, paint)
        yPosition += 25f
        canvas.drawText("   - Завершено ступеней: [данные]", 70f, yPosition, paint)
        yPosition += 35f

        canvas.drawText("3. Финансовая информация и взносы", 50f, yPosition, paint)
        yPosition += 25f
        canvas.drawText("   - Собрано средств: [данные]", 70f, yPosition, paint)

        pdfDocument.finishPage(page)

        // 2. Сохранение PDF в файл
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()

        // 3. Сохранение записи о отчёте в БД
        val report = ReportEntity(
            id = reportId,
            templateId = "default_template",
            level = level,
            clubId = clubId,
            periodStart = periodStart,
            periodEnd = periodEnd,
            format = "pdf",
            status = "draft",
            fileUrl = file.absolutePath,
            generatedBy = generatedBy,
            generatedAt = Date(),
            signed = false,
            signedAt = null,
            errorMessage = null
        )

        reportRepository.upsertReport(report)
        Result.success(reportId)
    } catch (e: Exception) {
        Result.failure(e)
    }
}