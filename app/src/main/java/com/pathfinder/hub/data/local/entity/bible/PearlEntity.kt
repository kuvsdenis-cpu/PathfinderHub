package com.pathfinder.hub.data.local.entity.bible

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pearls",
    foreignKeys = [ForeignKey(
        entity = PearlScheduleEntity::class,
        parentColumns = ["id"],
        childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["scheduleId", "weekNumber"], unique = true),
        Index("scheduleId"),
        Index("verseId"),
    ]
)
data class PearlEntity(
    @PrimaryKey val id: String,
    val scheduleId: String,
    val weekNumber: Int,
    val season: Int,
    val seasonTitle: String,
    val verseId: String?,
    val reference: String,
    val text: String?,
    val startDate: Long,
    val endDate: Long,
    val isExam: Boolean = false,
)
