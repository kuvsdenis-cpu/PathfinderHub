package com.pathfinder.hub.data.local.dao

import androidx.room.*
import com.pathfinder.hub.data.local.entity.reports.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Query("SELECT * FROM report_templates WHERE level = :level ORDER BY name ASC")
    fun observeTemplates(level: String): Flow<List<ReportTemplateEntity>>

    @Query("SELECT * FROM report_templates WHERE level = :level AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultTemplate(level: String): ReportTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: ReportTemplateEntity)

    @Query("SELECT * FROM reports WHERE clubId = :clubId ORDER BY generatedAt DESC")
    fun observeClubReports(clubId: String): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports ORDER BY generatedAt DESC LIMIT :limit")
    fun observeAllReports(limit: Int = 100): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE id = :reportId LIMIT 1")
    suspend fun getReportById(reportId: String): ReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(report: ReportEntity)

    // ИСПРАВЛЕНО: убран неиспользуемый параметр u, так как в ReportEntity нет поля signedBy
    @Query("UPDATE reports SET status = :status WHERE id = :id")
    suspend fun updateReportStatus(id: String, status: String)

    @Query("UPDATE reports SET signed = 1, signedAt = :at WHERE id = :id")
    suspend fun markSigned(id: String, at: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignature(signature: ReportSignatureEntity)

    @Insert
    suspend fun logHistory(history: ReportHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: AnalyticsSnapshotEntity)

    @Query("""SELECT * FROM analytics_snapshots WHERE clubId = :clubId
        ORDER BY periodEnd DESC LIMIT 1""")
    suspend fun getLatestSnapshot(clubId: String): AnalyticsSnapshotEntity?
}