package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.EventDao
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.planning.EventHonorEntity
import com.pathfinder.hub.data.local.entity.planning.EventOccurrenceEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class EventRepository @Inject constructor(
    private val dao: EventDao,
    private val syncQueueManager: SyncQueueManager
) {
    fun observeEvent(id: String): Flow<EventEntity?> = dao.observeEvent(id)
    suspend fun getEvent(id: String): EventEntity? = dao.getEvent(id)
    fun observeEventsInRange(clubId: String, from: Long, to: Long): Flow<List<EventEntity>> =
        dao.observeEventsInRange(clubId, from, to)
    fun observeByStatus(status: String): Flow<List<EventEntity>> = dao.observeByStatus(status)
    fun observeUpcoming(now: Long, limit: Int): Flow<List<EventEntity>> = dao.observeUpcoming(now, limit)

    suspend fun upsert(event: EventEntity) {
        dao.upsert(event)
        syncQueueManager.enqueue("event", event.id, "CREATE", event)
    }

    suspend fun upsertAll(events: List<EventEntity>) {
        dao.upsertAll(events)
        events.forEach { syncQueueManager.enqueue("event", it.id, "CREATE", it) }
    }

    suspend fun update(event: EventEntity) {
        dao.update(event)
        syncQueueManager.enqueue("event", event.id, "UPDATE", event)
    }

    suspend fun updateModeration(id: String, status: String, by: String, at: Long) {
        dao.updateModeration(id, status, by, at)
        dao.getEvent(id)?.let { syncQueueManager.enqueue("event", id, "UPDATE", it) }
    }

    suspend fun delete(event: EventEntity) {
        dao.delete(event)
        syncQueueManager.enqueue("event", event.id, "DELETE", event)
    }

    suspend fun getPendingSync(): List<EventEntity> = dao.getPendingSync()

    suspend fun upsertOccurrences(items: List<EventOccurrenceEntity>) = dao.upsertOccurrences(items)
    suspend fun getOccurrences(eventId: String): List<EventOccurrenceEntity> = dao.getOccurrences(eventId)
    suspend fun upsertEventHonors(items: List<EventHonorEntity>) = dao.upsertEventHonors(items)
    suspend fun getEventHonors(eventId: String): List<EventHonorEntity> = dao.getEventHonors(eventId)
}