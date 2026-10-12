package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "homework")
data class HomeworkEntity(
    @PrimaryKey val id: String, val clubId: String, val childId: String,
    val instructorId: String, val title: String, val description: String,
    val dueDate: Date,
    val rewardType: String = "text",       // "text" | "points" | "achievement"
    val rewardText: String? = null,
    val rewardPoints: Int = 0,
    val rewardAchievementId: String? = null,
    val status: String,
    val createdAt: Date, val approvedAt: Date?, val pendingSync: Boolean = false,

    // S7: Архивация
    val archivedAt: Date? = null,
    val purgeAfter: Date? = null,
)
