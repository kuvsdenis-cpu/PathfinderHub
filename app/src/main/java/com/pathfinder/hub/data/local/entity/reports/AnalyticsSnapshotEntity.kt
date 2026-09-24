package com.pathfinder.hub.data.local.entity.reports
import androidx.room.*
import java.util.Date

@Entity(tableName = "analytics_snapshots")
data class AnalyticsSnapshotEntity(
    @PrimaryKey val id: String, val clubId: String,
    val periodStart: Date, val periodEnd: Date, val attendanceRate: Float,
    val levelDistribution: String, val honorsByCategory: String,
    val activityByMonth: String, val createdAt: Date
)
