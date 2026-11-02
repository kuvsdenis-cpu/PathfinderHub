package com.pathfinder.hub.data.local.entity.bible

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_plan_days",
    foreignKeys = [ForeignKey(
        entity = ReadingPlanScheduleEntity::class,
        parentColumns = ["id"],
        childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["scheduleId", "dayNumber"], unique = true),
        Index("scheduleId"),
        Index("date"),
    ]
)
data class ReadingPlanDayEntity(
    @PrimaryKey val id: String,
    val scheduleId: String,
    val dayNumber: Int,
    val date: Long,
    val reference: String,
    val bookId: String?,
    val chapterFrom: Int?,
    val chapterTo: Int?,
    val passageText: String?,
)
