package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "achievement_progress",
    primaryKeys = ["userId", "achievementId"],
    indices = [Index("userId"), Index("status")])
data class AchievementProgressEntity(
    val userId: String, val achievementId: String, val status: String,
    val earnedAt: Date?, val revokedAt: Date?, val revokedBy: String?,
    val revokeReason: String?, val pendingSync: Boolean = false
)
