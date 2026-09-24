package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "level_completions", primaryKeys = ["userId", "levelId"])
data class LevelCompletionEntity(
    val userId: String, val levelId: String, val completedAt: Date, val approvedBy: String
)
