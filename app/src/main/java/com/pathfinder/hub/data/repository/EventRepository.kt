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
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.local.entity.audit.AuditLogEntity
import com.google.gson.Gson
import java.util.UUID
import java.util.Date
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class EventRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: EventDao,
    private val participantDao: EventParticipantDao,
    private val consentDao: EventConsentDao,
    private val syncQueueManager: SyncQueueManager,
    private val auditRepo: AuditRepository
) {
    private val gson = Gson()

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
            auditRepo.log(AuditLogEntity(
                id = UUID.randomUUID().toString(), targetType = "event", targetId = event.id,
                action = "create", userId = event.createdBy, userRole = "",
                clubId = event.clubId, conferenceId = null, timestamp = Date(),
                oldValue = null, newValue = gson.toJson(event), reason = null,
                visibleToAuthor = true, ipHash = null, userAgent = null
            ))
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
            auditRepo.log(AuditLogEntity(
                id = UUID.randomUUID().toString(), targetType = "event", targetId = event.id,
                action = "update", userId = event.createdBy, userRole = "",
                clubId = event.clubId, conferenceId = null, timestamp = Date(),
                oldValue = null, newValue = gson.toJson(event), reason = null,
                visibleToAuthor = true, ipHash = null, userAgent = null
            ))
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
            auditRepo.log(AuditLogEntity(
                id = UUID.randomUUID().toString(), targetType = "event", targetId = event.id,
                action = "delete", userId = event.createdBy, userRole = "",
                clubId = event.clubId, conferenceId = null, timestamp = Date(),
                oldValue = gson.toJson(event), newValue = null, reason = null,
                visibleToAuthor = true, ipHash = null, userAgent = null
            ))
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

    suspend fun deleteOccurrencesForEvent(eventId: String) {
        db.withTransaction { dao.deleteOccurrencesForEvent(eventId) }
    }

    suspend fun getOccurrencesInRange(from: Long, to: Long): List<EventOccurrenceEntity> =
        dao.getOccurrencesInRange(from, to)

    suspend fun getOccurrencesForEvents(eventIds: List<String>, from: Long, to: Long): List<EventOccurrenceEntity> =
        if (eventIds.isEmpty()) emptyList()
        else dao.getOccurrencesForEvents(eventIds, from, to)

    /**
     * UX-5: сохранить повторяющееся событие.
     *
     * @param isNew true — создаёт event + N occurrences + participants в одной транзакции.
     *              false — пересоздаёт occurrences (delete old + generate new), participants не трогает.
     * @param memberIds используется только при isNew=true; null — событие без участников.
     *
     * Если [until]=null или [event].isRecurring=false — occurrences не создаются (старые удаляются).
     */
    suspend fun saveRecurringEvent(
        event: EventEntity,
        frequency: String,
        until: Date?,
        byDayOfWeek: Int?,
        byDayOfMonth: Int?,
        memberIds: List<String>?,
        isNew: Boolean,
    ) {
        db.withTransaction {
            dao.upsert(event)

            // Пересоздать occurrences
            val oldOccurrences = if (!isNew) dao.getOccurrences(event.id) else emptyList()
            dao.deleteOccurrencesForEvent(event.id)
            val newOccurrences = if (event.isRecurring && until != null && !until.before(event.dateStart)) {
                generateOccurrences(event, frequency, until, byDayOfWeek, byDayOfMonth)
            } else emptyList()
            if (newOccurrences.isNotEmpty()) {
                dao.upsertOccurrences(newOccurrences)
            }

            // Enqueue: удалить старые occurrences, которых нет в новых
            val newIds = newOccurrences.map { it.id }.toSet()
            oldOccurrences.filter { it.id !in newIds }.forEach { old ->
                if (!event.clubId.isNullOrBlank()) {
                    syncQueueManager.enqueue(
                        "event_occurrence", old.id, "delete",
                        mapOf(
                            "id" to old.id,
                            "eventId" to old.eventId,
                            "clubId" to event.clubId,
                            "pendingSync" to false,
                        )
                    )
                }
            }
            // Enqueue: новые occurrences
            newOccurrences.forEach { occ ->
                if (!event.clubId.isNullOrBlank()) {
                    syncQueueManager.enqueue(
                        "event_occurrence", occ.id, "upsert",
                        mapOf(
                            "id" to occ.id,
                            "eventId" to occ.eventId,
                            "dateStart" to occ.dateStart.time,
                            "dateEnd" to occ.dateEnd.time,
                            "status" to occ.status,
                            "clubId" to event.clubId,
                            "pendingSync" to false,
                        )
                    )
                }
            }

            // Participants — только при create
            if (isNew && memberIds != null) {
                val now = Date(System.currentTimeMillis())
                val allMemberIds = (memberIds + event.createdBy).distinct()
                val participants = allMemberIds.map { uid ->
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
                participants.forEach { p ->
                    syncQueueManager.enqueue(
                        "event_participant", p.id, "CREATE",
                        mapOf(
                            "id" to p.id,
                            "eventId" to p.eventId,
                            "userId" to p.userId,
                            "status" to p.status,
                            "invitedAt" to p.invitedAt.time,
                            "respondedAt" to p.respondedAt?.time,
                            "attendedAt" to p.attendedAt?.time,
                            "clubId" to event.clubId,
                            "pendingSync" to false,
                        )
                    )
                }
                memberIds.forEach { uid ->
                    auditRepo.log(AuditLogEntity(
                        id = UUID.randomUUID().toString(), targetType = "event_participant",
                        targetId = "${event.id}:$uid", action = "invite", userId = uid, userRole = "",
                        clubId = event.clubId, conferenceId = null, timestamp = Date(),
                        oldValue = null, newValue = null, reason = null,
                        visibleToAuthor = true, ipHash = null, userAgent = null
                    ))
                }
            }

            syncQueueManager.enqueue("event", event.id, if (isNew) "CREATE" else "UPDATE", event)
            auditRepo.log(AuditLogEntity(
                id = UUID.randomUUID().toString(), targetType = "event", targetId = event.id,
                action = if (isNew) "create" else "update", userId = event.createdBy, userRole = "",
                clubId = event.clubId, conferenceId = null, timestamp = Date(),
                oldValue = null, newValue = gson.toJson(event), reason = null,
                visibleToAuthor = true, ipHash = null, userAgent = null
            ))
        }
    }

    /**
     * UX-5: сгенерировать даты серии.
     * Максимум 500 occurrences (защита от «ежедневно до 2100»).
     * id = "${event.id}:${millis}" — стабильный, идемпотентный (REPLACE при повторной вставке).
     * duration сохраняется как в головном событии.
     */
    private fun generateOccurrences(
        event: EventEntity,
        frequency: String,
        until: Date,
        byDayOfWeek: Int?,
        byDayOfMonth: Int?,
    ): List<EventOccurrenceEntity> {
        val maxOccurrences = 500
        val duration = event.dateEnd.time - event.dateStart.time
        val cal = Calendar.getInstance().apply { timeInMillis = event.dateStart.time }
        val end = until.time
        val result = mutableListOf<EventOccurrenceEntity>()

        // Корректировка первой даты
        if (frequency in listOf("weekly", "biweekly") && byDayOfWeek != null) {
            var guard = 0
            while (cal.get(Calendar.DAY_OF_WEEK) != byDayOfWeek && guard < 14) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                guard++
            }
            if (cal.timeInMillis > end) return emptyList()
        } else if (frequency == "monthly" && byDayOfMonth != null) {
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, minOf(byDayOfMonth, maxDay))
            if (cal.timeInMillis < event.dateStart.time) {
                cal.add(Calendar.MONTH, 1)
                val nextMax = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, minOf(byDayOfMonth, nextMax))
            }
        }

        var current = cal.timeInMillis
        while (current <= end && result.size < maxOccurrences) {
            result.add(
                EventOccurrenceEntity(
                    id = "${event.id}:$current",
                    eventId = event.id,
                    dateStart = Date(current),
                    dateEnd = Date(current + duration),
                    status = "scheduled",
                    overrideData = null,
                )
            )
            val next = Calendar.getInstance().apply { timeInMillis = current }
            when (frequency) {
                "daily" -> next.add(Calendar.DAY_OF_MONTH, 1)
                "weekly" -> next.add(Calendar.DAY_OF_MONTH, 7)
                "biweekly" -> next.add(Calendar.DAY_OF_MONTH, 14)
                "monthly" -> {
                    val dayOfMonth = byDayOfMonth ?: next.get(Calendar.DAY_OF_MONTH)
                    next.add(Calendar.MONTH, 1)
                    val maxDay = next.getActualMaximum(Calendar.DAY_OF_MONTH)
                    next.set(Calendar.DAY_OF_MONTH, minOf(dayOfMonth, maxDay))
                }
                "yearly" -> next.add(Calendar.YEAR, 1)
                else -> break
            }
            current = next.timeInMillis
        }
        return result
    }

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
            // Создатель всегда в участниках — иначе он не увидит своё событие
            // (observeActiveInRangeForUser фильтрует по event_participants).
            val allMemberIds = (memberIds + event.createdBy).distinct()
            val participants = allMemberIds.map { uid ->
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
            // B: enqueue participant-ов отдельно  clubId нет в entity, передаём map
            participants.forEach { p ->
                syncQueueManager.enqueue(
                    "event_participant", p.id, "CREATE",
                    mapOf(
                        "id" to p.id,
                        "eventId" to p.eventId,
                        "userId" to p.userId,
                        "status" to p.status,
                        "invitedAt" to p.invitedAt.time,
                        "respondedAt" to p.respondedAt?.time,
                        "attendedAt" to p.attendedAt?.time,
                        "clubId" to event.clubId,
                        "pendingSync" to false,
                    )
                )
            }
            auditRepo.log(AuditLogEntity(
                id = UUID.randomUUID().toString(), targetType = "event", targetId = event.id,
                action = "create", userId = event.createdBy, userRole = "",
                clubId = event.clubId, conferenceId = null, timestamp = Date(),
                oldValue = null, newValue = gson.toJson(event), reason = null,
                visibleToAuthor = true, ipHash = null, userAgent = null
            ))
            memberIds.forEach { uid ->
                auditRepo.log(AuditLogEntity(
                    id = UUID.randomUUID().toString(), targetType = "event_participant",
                    targetId = "${event.id}:$uid", action = "invite", userId = uid, userRole = "",
                    clubId = event.clubId, conferenceId = null, timestamp = Date(),
                    oldValue = null, newValue = null, reason = null,
                    visibleToAuthor = true, ipHash = null, userAgent = null
                ))
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
            // B: enqueue UPDATE participant
            val ev = dao.getEvent(eventId)
            participantDao.getParticipant(eventId, userId)?.let { p ->
                if (ev != null && !ev.clubId.isNullOrBlank()) {
                    syncQueueManager.enqueue(
                        "event_participant", p.id, "UPDATE",
                        mapOf(
                            "id" to p.id,
                            "eventId" to p.eventId,
                            "userId" to p.userId,
                            "status" to p.status,
                            "invitedAt" to p.invitedAt.time,
                            "respondedAt" to p.respondedAt?.time,
                            "attendedAt" to p.attendedAt?.time,
                            "clubId" to ev.clubId,
                            "pendingSync" to false,
                        )
                    )
                }
            }
        }
    }

    suspend fun updateParticipantQuestions(
        eventId: String, userId: String, questions: List<String>
    ) {
        db.withTransaction {
            participantDao.updateQuestions(eventId, userId, questions)
        }
    }
}