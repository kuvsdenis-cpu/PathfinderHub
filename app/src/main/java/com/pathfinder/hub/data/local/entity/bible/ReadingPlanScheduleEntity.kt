package com.pathfinder.hub.data.local.entity.bible

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_plan_schedules")
data class ReadingPlanScheduleEntity(
    @PrimaryKey val id: String,
    val year: Int,
    val title: String,
    val kind: String,
    val version: Int,
    val source: String,
    val importedAt: Long,
    val importedBy: String?,
    val isActive: Boolean = true,
)
