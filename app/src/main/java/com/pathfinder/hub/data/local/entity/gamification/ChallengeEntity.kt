package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val id: String, val clubId: String, val title: String,
    val description: String, val type: String, val goal: Int, val unit: String,
    val dateStart: Date, val dateEnd: Date, val createdBy: String,
    val status: String, val participants: List<String>, val rewards: List<String>
)
