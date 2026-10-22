package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "digital_uniforms")
data class DigitalUniformEntity(
    @PrimaryKey val userId: String, val shirtColor: String, val tieColor: String,
    val earnedBadges: List<BadgeItem>, val earnedChevrons: List<ChevronItem>,
    val specialMarks: List<SpecialMarkItem>, val updatedAt: Date,
    val pendingSync: Boolean = false, val syncedAt: Date? = null
)

data class BadgeItem(val honorId: String, val position: Int, val earnedAt: Date)
data class ChevronItem(val levelId: String, val position: Int, val earnedAt: Date)
data class SpecialMarkItem(val achievementId: String, val icon: String, val earnedAt: Date)
