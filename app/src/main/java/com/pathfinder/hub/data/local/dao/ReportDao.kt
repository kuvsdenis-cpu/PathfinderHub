package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.reports.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Query("SELECT * FROM report_templates WHERE level = :level")
    fun observeTemplates(level: String): Flow<List<ReportTemplateEntity>>
    @Query("SELECT * FROM report_templates WHERE isDefault = 1 AND level = :level LIMIT 1")
    suspend fun getDefaultTemplate(level: String): ReportTemplateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(t: ReportTemplateEntity)
    @Query("SELECT * FROM reports WHERE clubId = :clubId ORDER BY generatedAt DESC")
    fun observeClubReports(clubId: String): Flow<List<ReportEntity>>
    @Query("SELECT * FROM reports ORDER BY generatedAt DESC LIMIT :limit")
    fun observeAllReports(limit: Int = 100): Flow<List<ReportEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(r: ReportEntity)
    @Query("UPDATE reports SET status = :status, fileUrl = :url WHERE id = :id")
    suspend fun updateReportStatus(id: String, status: String, url: String?)
    @Query("UPDATE reports SET signed = 1, signedAt = :at WHERE id = :id")
    suspend fun markSigned(id: String, at: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignature(s: ReportSignatureEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun logHistory(h: ReportHistoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(s: AnalyticsSnapshotEntity)
    @Query("""SELECT * FROM analytics_snapshots WHERE clubId = :clubId
        ORDER BY periodEnd DESC LIMIT 1""")
    suspend fun getLatestSnapshot(clubId: String): AnalyticsSnapshotEntity?
}
