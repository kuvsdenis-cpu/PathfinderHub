package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.EventDao
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.planning.EventHonorEntity
import com.pathfinder.hub.data.local.entity.planning.EventParticipantEntity
import com.pathfinder.hub.data.local.dao.EventParticipantDao
import com.pathfinder.hub.data.local.dao.EventConsentDao
import com.pathfinder.hub.data.local.entity.planning.EventOccurrenceEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import java.util.Date
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class EventRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: EventDao,
    private val participantDao: EventParticipantDao,
    private val consentDao: EventConsentDao,
    private val syncQueueManager: SyncQueueManager
) {
    fun observeEvent(id: String): Flow<EventEntity?> = dao.observeEvent(id)
    suspend fun getEvent(id: String): EventEntity? = dao.getEvent(id)

    /** Опубликованные события клуба за период. */
    suspend fun getPublishedInPeriod(clubId: String, from: Long, to: Long): List<EventEntity> =
        dao.getPublishedInPeriod(clubId, from, to)
    fun observeEventsInRange(clubId: String, from: Long, to: Long): Flow<List<EventEntity>> =
        dao.observeEventsInRange(clubId, from, to)

    // ---------- Архивация ----------
    fun observeActiveInRange(clubId: String, from: Long, to: Long): Flow<List<EventEntity>> =
        dao.observeActiveInRange(clubId, from, to)

    fun observeArchivedByClub(clubId: String): Flow<List<EventEntity>> =
        dao.observeArchivedByClub(clubId)

    /**
     * Архивировать событие. purgeAfter = archivedAt + 90 дней.
     */
    suspend fun archiveEvent(id: String) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val purgeAfter = now + 90L * 24 * 3600 * 1000
            dao.archive(id, now, purgeAfter)
            dao.getEvent(id)?.let { syncQueueManager.enqueue("event", id, "UPDATE", it) }
        }
    }

    suspend fun restoreEvent(id: String) {
        db.withTransaction {
            dao.restore(id)
            dao.getEvent(id)?.let { syncQueueManager.enqueue("event", id, "UPDATE", it) }
        }
    }

    /** Удалить всё, у чего purgeAfter < now. Возвращает количество. */
    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): Int {
        return db.withTransaction {
            participantDao.deleteOrphansForExpiredEvents(now)
            consentDao.deleteOrphansForExpiredEvents(now)
            dao.purgeExpired(now)
        }
    }
    fun observeByStatus(status: String): Flow<List<EventEntity>> = dao.observeByStatus(status)
    fun observeUpcoming(now: Long, limit: Int): Flow<List<EventEntity>> = dao.observeUpcoming(now, limit)

    suspend fun upsert(event: EventEntity) {
        db.withTransaction {
            dao.upsert(event)
            syncQueueManager.enqueue("event", event.id, "CREATE", event)
        }
    }

    suspend fun upsertAll(events: List<EventEntity>) {
        db.withTransaction {
            dao.upsertAll(events)
            events.forEach { syncQueueManager.enqueue("event", it.id, "CREATE", it) }
        }
    }

    suspend fun update(event: EventEntity) {
        db.withTransaction {
            dao.update(event)
            syncQueueManager.enqueue("event", event.id, "UPDATE", event)
        }
    }

    suspend fun updateModeration(id: String, status: String, by: String, at: Long) {
        db.withTransaction {
            dao.updateModeration(id, status, by, at)
            dao.getEvent(id)?.let { syncQueueManager.enqueue("event", id, "UPDATE", it) }
        }
    }

    suspend fun delete(event: EventEntity) {
        db.withTransaction {
            dao.delete(event)
            syncQueueManager.enqueue("event", event.id, "DELETE", event)
        }
    }

    suspend fun getPendingSync(): List<EventEntity> = dao.getPendingSync()

    /**
     * Кэшировать событие чужого клуба локально (только Room, без Firestore).
     * Используется cross-club: teen redeemed код → забирает event из Firestore
     * напрямую → сохраняет в Room.
     */
    suspend fun cacheEventLocally(event: EventEntity) {
        dao.upsert(event)
    }

    suspend fun upsertOccurrences(items: List<EventOccurrenceEntity>) {
        db.withTransaction { dao.upsertOccurrences(items) }
    }

    suspend fun getOccurrences(eventId: String): List<EventOccurrenceEntity> = dao.getOccurrences(eventId)

    suspend fun upsertEventHonors(items: List<EventHonorEntity>) {
        db.withTransaction { dao.upsertEventHonors(items) }
    }

    suspend fun getEventHonors(eventId: String): List<EventHonorEntity> = dao.getEventHonors(eventId)

    // ---------- A2: групповые события ----------
    fun observeEventsForUser(clubId: String, userId: String, from: Long, to: Long): Flow<List<EventEntity>> =
        dao.observeActiveInRangeForUser(clubId, userId, from, to)

    fun observeActiveByClubForUser(clubId: String, userId: String): Flow<List<EventEntity>> =
        dao.observeActiveByClubForUser(clubId, userId)

    fun observeParticipants(eventId: String): Flow<List<EventParticipantEntity>> =
        participantDao.observeParticipants(eventId)

    suspend fun getParticipant(eventId: String, userId: String): EventParticipantEntity? =
        participantDao.getParticipant(eventId, userId)

    /**
     * Создать событие и назначить его N участникам (per-user статус invited).
     * Зеркало TaskRepository.createTaskForGroup.
     */
    suspend fun createEventForGroup(event: EventEntity, memberIds: List<String>) {
        db.withTransaction {
            dao.upsert(event)
            val now = Date(System.currentTimeMillis())
            val participants = memberIds.map { uid ->
                EventParticipantEntity(
                    id = "${event.id}:$uid",
                    eventId = event.id,
                    userId = uid,
                    status = "invited",
                    invitedAt = now,
                    respondedAt = null,
                    attendedAt = null
                )
            }
            participantDao.upsertAll(participants)
            syncQueueManager.enqueue("event", event.id, "CREATE", event)
            participants.forEach {
                syncQueueManager.enqueue("event_participant", it.id, "CREATE", it)
            }
        }
    }

    suspend fun updateParticipantStatus(
        eventId: String, userId: String, status: String
    ) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val respondedAt = if (status == "accepted" || status == "declined") now else null
            val attendedAt = if (status == "attended") now else null

            // A3: upsert-семантика — если participant нет, создаём на лету.
            val existing = participantDao.getParticipant(eventId, userId)
            if (existing == null) {
                participantDao.upsert(
                    EventParticipantEntity(
                        id = "$eventId:$userId",
                        eventId = eventId,
                        userId = userId,
                        status = status,
                        invitedAt = Date(now),
                        respondedAt = respondedAt?.let { Date(it) },
                        attendedAt = attendedAt?.let { Date(it) },
                        pendingSync = true,
                    )
                )
            } else {
                participantDao.updateStatus(eventId, userId, status, respondedAt, attendedAt)
            }
            participantDao.getParticipant(eventId, userId)?.let {
                syncQueueManager.enqueue("event_participant", it.id, "UPDATE", it)
            }
        }
    }

    suspend fun updateParticipantQuestions(
        eventId: String, userId: String, questions: List<String>
    ) {
        db.withTransaction {
            participantDao.updateQuestions(eventId, userId, questions)
            participantDao.getParticipant(eventId, userId)?.let {
                syncQueueManager.enqueue("event_participant", it.id, "UPDATE", it)
            }
        }
    }
}