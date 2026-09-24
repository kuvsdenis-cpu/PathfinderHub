package com.pathfinder.hub.data.local.relation
import androidx.room.*
import com.pathfinder.hub.data.local.entity.planning.*

data class EventWithDetails(
    @Embedded val event: EventEntity,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val tasks: List<TaskEntity>,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val occurrences: List<EventOccurrenceEntity>
)
