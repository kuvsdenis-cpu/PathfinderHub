package com.pathfinder.hub.data.local.entity.conference

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Мероприятие уровня конференции.
 *
 * level:
 *   "conference"    — вся конференция
 *   "regional"      — региональное
 *   "meeting"       — собрание
 *
 * visibility:
 *   "all"           — все клубы конференции
 *   "invited"       — только приглашённые клубы (invitedClubs)
 *
 * status:
 *   "draft" | "published" | "cancelled" | "completed"
 */
@Entity(
    tableName = "conference_events",
    indices = [Index("conferenceId"), Index("status"), Index("dateStart")]
)
data class ConferenceEventEntity(
    @PrimaryKey val id: String,
    val conferenceId: String,
    val level: String,
    val type: String,
    val title: String,
    val description: String,
    val dateStart: Date,
    val dateEnd: Date,
    val timezone: String,
    val location: String,
    val invitedClubs: List<String> = emptyList(),
    val visibility: String = "all",
    val createdBy: String,
    val status: String = "published",
    val publishedAt: Date? = null,
    val cancelledReason: String? = null,
    val pendingSync: Boolean = false,
    val createdAt: Date,
    val updatedAt: Date,
)
