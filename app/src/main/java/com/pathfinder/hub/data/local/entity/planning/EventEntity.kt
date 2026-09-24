package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*
import java.util.Date

@Entity(tableName = "events",
    indices = [Index("clubId"), Index("status"), Index("dateStart")])
data class EventEntity(
    @PrimaryKey val id: String, val level: String, val clubId: String?,
    val type: String, val title: String, val description: String,
    val dateStart: Date, val dateEnd: Date, val timezone: String,
    val location: String, val isRecurring: Boolean, val recurrenceRule: String?,
    val parentEventId: String?, val createdBy: String, val status: String,
    val autoModerationResult: String, val moderatedBy: String?, val moderatedAt: Date?,
    val reviewDeadline: Date?, val hiddenReasonCode: String?, val hiddenReasonNote: String?,
    val visibility: String, val publishedAt: Date?, val pendingSync: Boolean = false
)
