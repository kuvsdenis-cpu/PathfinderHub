package com.pathfinder.hub.data.local.entity.bible

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pearl_schedules")
data class PearlScheduleEntity(
    @PrimaryKey val id: String,
    val year: Int,
    val title: String,
    val version: Int,
    val source: String,
    val importedAt: Long,
    val importedBy: String?,
    val isActive: Boolean = true,
)
