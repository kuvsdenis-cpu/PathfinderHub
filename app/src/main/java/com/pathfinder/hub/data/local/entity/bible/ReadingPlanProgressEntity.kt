package com.pathfinder.hub.data.local.entity.bible

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_plan_progress",
    indices = [
        Index(value = ["userId", "dayId"], unique = true),
        Index("userId"),
    ]
)
data class ReadingPlanProgressEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dayId: String,
    val completedAt: Long,
    val pendingSync: Boolean = true,
)
