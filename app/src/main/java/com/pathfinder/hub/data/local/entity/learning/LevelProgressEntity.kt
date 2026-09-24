package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "level_progress",
    primaryKeys = ["userId", "requirementId"],
    indices = [Index("userId"), Index("levelId"), Index("status")])
data class LevelProgressEntity(
    val userId: String, val levelId: String, val requirementId: String,
    val status: String, val submittedAt: Date?, val approvedBy: String?,
    val approvedAt: Date?, val comment: String?, val pendingSync: Boolean = false
)
