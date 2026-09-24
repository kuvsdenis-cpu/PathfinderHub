package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "challenge_progress", primaryKeys = ["challengeId", "userId"])
data class ChallengeProgressEntity(
    val challengeId: String, val userId: String, val progress: Int,
    val completedAt: Date?, val rewardClaimed: Boolean, val pendingSync: Boolean = false
)
