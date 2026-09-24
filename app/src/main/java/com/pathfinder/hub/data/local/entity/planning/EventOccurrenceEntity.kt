package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*
import java.util.Date

@Entity(tableName = "event_occurrences", indices = [Index("eventId")])
data class EventOccurrenceEntity(
    @PrimaryKey val id: String, val eventId: String,
    val dateStart: Date, val dateEnd: Date, val status: String,
    val overrideData: Map<String, Any>?
)
