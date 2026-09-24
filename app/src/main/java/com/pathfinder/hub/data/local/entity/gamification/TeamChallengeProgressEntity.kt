package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "team_challenge_progress")
data class TeamChallengeProgressEntity(
    @PrimaryKey val challengeId: String, val clubId: String,
    val totalProgress: Int, val goal: Int, val teamReputation: Int,
    val participants: List<TeamParticipant>, val completedAt: Date?
)

data class TeamParticipant(
    val userId: String, val progress: Int, val personalReputation: Int
)
