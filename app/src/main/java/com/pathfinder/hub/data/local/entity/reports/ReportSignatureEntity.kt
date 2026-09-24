package com.pathfinder.hub.data.local.entity.reports
import androidx.room.*
import java.util.Date

@Entity(tableName = "report_signatures")
data class ReportSignatureEntity(
    @PrimaryKey val reportId: String, val signedBy: String,
    val signatureType: String, val signatureData: String, val signedAt: Date
)
