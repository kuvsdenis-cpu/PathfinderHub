package com.pathfinder.hub.data.local.entity.reports
import androidx.room.*
import java.util.Date

@Entity(tableName = "report_history")
data class ReportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: String, val userId: String, val action: String, val timestamp: Date
)
