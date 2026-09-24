package com.pathfinder.hub.data.local.entity.reports
import androidx.room.*
import java.util.Date

@Entity(tableName = "reports", indices = [Index("clubId"), Index("status")])
data class ReportEntity(
    @PrimaryKey val id: String, val templateId: String, val level: String,
    val clubId: String?, val periodStart: Date, val periodEnd: Date,
    val format: String = "pdf", val status: String, val fileUrl: String?,
    val generatedBy: String, val generatedAt: Date, val signed: Boolean,
    val signedAt: Date?, val errorMessage: String?
)
