package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String, val name: String, val description: String,
    val category: String, val icon: String, val condition: String,
    val isHidden: Boolean = false
)
