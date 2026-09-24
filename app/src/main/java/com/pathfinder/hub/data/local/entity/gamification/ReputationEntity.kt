package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "reputations")
data class ReputationEntity(
    @PrimaryKey val userId: String, val clubId: String,
    val personal: Int, val team: Int, val updatedAt: Date
)
