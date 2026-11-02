package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*
import java.util.Date

/**
 * A2: участник события (per-user статус).
 * id = "<eventId>:<userId>".
 * Статусы: invited | accepted | declined | attended | missed.
 */
@Entity(
    tableName = "event_participants",
    indices = [Index("eventId"), Index("userId"), Index("status")]
)
data class EventParticipantEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val userId: String,
    val status: String,
    val invitedAt: Date,
    val respondedAt: Date?,
    val attendedAt: Date?,
    val pendingSync: Boolean = false,
    val questions: List<String> = emptyList()
)
