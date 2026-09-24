package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "homework")
data class HomeworkEntity(
    @PrimaryKey val id: String, val clubId: String, val childId: String,
    val instructorId: String, val title: String, val description: String,
    val dueDate: Date, val reward: String?, val status: String,
    val createdAt: Date, val approvedAt: Date?, val pendingSync: Boolean = false
)
