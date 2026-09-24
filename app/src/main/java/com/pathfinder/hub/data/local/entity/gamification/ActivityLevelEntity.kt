package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "activity_levels")
data class ActivityLevelEntity(
    @PrimaryKey val userId: String, val currentLevel: Int,
    val achievementsCount: Int, val updatedAt: Date,
    val history: List<ActivityHistoryItem>
)

data class ActivityHistoryItem(
    val stepId: String, val level: Int, val achievementsCount: Int
)
