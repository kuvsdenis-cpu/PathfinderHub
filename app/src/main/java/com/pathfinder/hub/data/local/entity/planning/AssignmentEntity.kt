package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*
import java.util.Date

@Entity(tableName = "assignments", indices = [Index("taskId"), Index("userId")])
data class AssignmentEntity(
    @PrimaryKey val id: String, val taskId: String, val userId: String,
    val role: String?, val status: String, val acceptedAt: Date?,
    val completedAt: Date?, val confirmedBy: String?, val confirmedAt: Date?,
    val pendingSync: Boolean = false
)
