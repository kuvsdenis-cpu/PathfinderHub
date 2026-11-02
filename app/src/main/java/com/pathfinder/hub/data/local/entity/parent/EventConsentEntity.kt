package com.pathfinder.hub.data.local.entity.parent

import androidx.room.Entity
import androidx.room.Index
import java.util.Date

/**
 * Согласие родителя на участие ребёнка в мероприятии.
 *
 * Статусы:
 *   "pending"   - запрошено, родитель ещё не ответил
 *   "approved"  - родитель согласен
 *   "declined"  - родитель против
 */
@Entity(
    tableName = "event_consents",
    primaryKeys = ["eventId", "childId", "parentId"],
    indices = [Index("eventId"), Index("childId"), Index("parentId"), Index("status")]
)
data class EventConsentEntity(
    val eventId: String,
    val childId: String,
    val parentId: String,
    val clubId: String,
    val status: String = "pending",
    val requestedBy: String,
    val requestedAt: Date,
    val respondedAt: Date? = null,
    val comment: String? = null,
    val documentUrl: String? = null,
)
