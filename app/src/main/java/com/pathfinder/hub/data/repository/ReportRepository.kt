package com.pathfinder.hub.data.repository

import android.content.Context
import com.pathfinder.hub.data.local.dao.ReportDao
import com.pathfinder.hub.data.local.entity.reports.*
import com.pathfinder.hub.data.reports.ReportDataCollector
import com.pathfinder.hub.data.reports.ReportPdfGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ReportDao,
    private val collector: ReportDataCollector,
    private val pdfGenerator: ReportPdfGenerator,
) {
    fun observeTemplates(l: String): Flow<List<ReportTemplateEntity>> = dao.observeTemplates(l)
    suspend fun getDefaultTemplate(l: String): ReportTemplateEntity? = dao.getDefaultTemplate(l)
    suspend fun upsertTemplate(t: ReportTemplateEntity) = dao.upsertTemplate(t)
    fun observeClubReports(c: String): Flow<List<ReportEntity>> = dao.observeClubReports(c)
    fun observeAllReports(l: Int = 100): Flow<List<ReportEntity>> = dao.observeAllReports(l)

    suspend fun getReportById(reportId: String): ReportEntity? = dao.getReportById(reportId)

    suspend fun upsertReport(r: ReportEntity) = dao.upsertReport(r)

    // ---------- A3.3: Архивация ----------

    fun observeArchivedReportsByClub(clubId: String): Flow<List<ReportEntity>> =
        dao.observeArchivedReportsByClub(clubId)

    /** Архивировать отчёт. PDF-файл НЕ трогаем (см. решение Б). */
    suspend fun archiveReport(id: String) {
        val now = System.currentTimeMillis()
        val purgeAfter = now + 90L * 24 * 3600 * 1000
        dao.archiveReport(id, now, purgeAfter)
    }

    suspend fun restoreReport(id: String) {
        dao.restoreReport(id)
    }

    /** Удалить записи, у которых purgeAfter < now. PDF-файлы остаются в кэше. */
    suspend fun purgeExpiredReports(now: Long = System.currentTimeMillis()): Int {
        return dao.purgeExpiredReports(now)
    }

    /**
     * Сгенерировать PDF для отчёта:
     *   1. Собрать данные за период
     *   2. Сгенерировать PDF
     *   3. Сохранить fileUrl и статус 'generated'
     */
    suspend fun generateReport(reportId: String): Result<File> {
        return try {
            val report = dao.getReportById(reportId)
                ?: return Result.failure(IllegalStateException("Отчёт не найден"))

            val clubId = report.clubId
                ?: return Result.failure(IllegalStateException("У отчёта нет клуба"))

            val data = collector.collect(
                clubId = clubId,
                periodStart = report.periodStart,
                periodEnd = report.periodEnd,
                report = report,
            )

            val file = pdfGenerator.generate(context, report, data)

            dao.upsertReport(
                report.copy(
                    status = "generated",
                    fileUrl = file.absolutePath,
                    errorMessage = null,
                )
            )
            Result.success(file)
        } catch (e: Exception) {
            val report = dao.getReportById(reportId)
            if (report != null) {
                dao.upsertReport(report.copy(status = "error", errorMessage = e.message))
            }
            Result.failure(e)
        }
    }

    // ИСПРАВЛЕНО: параметр u игнорируется при вызове dao, чтобы избежать ошибки Unused parameter в Room
    suspend fun updateReportStatus(id: String, s: String, u: String?) = dao.updateReportStatus(id, s)

    suspend fun markSigned(id: String, at: Long) = dao.markSigned(id, at)
    suspend fun upsertSignature(s: ReportSignatureEntity) = dao.upsertSignature(s)
    suspend fun logHistory(h: ReportHistoryEntity) = dao.logHistory(h)
    suspend fun upsertSnapshot(s: AnalyticsSnapshotEntity) = dao.upsertSnapshot(s)
    suspend fun getLatestSnapshot(c: String): AnalyticsSnapshotEntity? = dao.getLatestSnapshot(c)
}