package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.ReportDao
import com.pathfinder.hub.data.local.entity.reports.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ReportRepository @Inject constructor(private val dao: ReportDao) {
    fun observeTemplates(l: String): Flow<List<ReportTemplateEntity>> = dao.observeTemplates(l)
    suspend fun getDefaultTemplate(l: String): ReportTemplateEntity? = dao.getDefaultTemplate(l)
    suspend fun upsertTemplate(t: ReportTemplateEntity) = dao.upsertTemplate(t)
    fun observeClubReports(c: String): Flow<List<ReportEntity>> = dao.observeClubReports(c)
    fun observeAllReports(l: Int = 100): Flow<List<ReportEntity>> = dao.observeAllReports(l)

    suspend fun getReportById(reportId: String): ReportEntity? = dao.getReportById(reportId)

    suspend fun upsertReport(r: ReportEntity) = dao.upsertReport(r)

    // ИСПРАВЛЕНО: параметр u игнорируется при вызове dao, чтобы избежать ошибки Unused parameter в Room
    suspend fun updateReportStatus(id: String, s: String, u: String?) = dao.updateReportStatus(id, s)

    suspend fun markSigned(id: String, at: Long) = dao.markSigned(id, at)
    suspend fun upsertSignature(s: ReportSignatureEntity) = dao.upsertSignature(s)
    suspend fun logHistory(h: ReportHistoryEntity) = dao.logHistory(h)
    suspend fun upsertSnapshot(s: AnalyticsSnapshotEntity) = dao.upsertSnapshot(s)
    suspend fun getLatestSnapshot(c: String): AnalyticsSnapshotEntity? = dao.getLatestSnapshot(c)
}