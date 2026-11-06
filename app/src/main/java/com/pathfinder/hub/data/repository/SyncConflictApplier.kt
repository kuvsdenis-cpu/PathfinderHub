package com.pathfinder.hub.data.repository

import android.util.Log
import com.google.gson.Gson
import com.pathfinder.hub.data.local.dao.CustomGroupDao
import com.pathfinder.hub.data.local.dao.EventParticipantDao
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.local.entity.offline.SyncConflictEntity
import com.pathfinder.hub.data.local.entity.planning.EventParticipantEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Применяет решение пользователя к фактической сущности.
 *
 * Поддержаны типы: event_participant, book, custom_group, user.
 *
 * Логика:
 *   chooseLocal=true  → upsert/update с pendingSync=true, старая запись в очереди
 *                       остаётся (SyncWorker отправит при следующей синхронизации).
 *   chooseLocal=false → upsert/update с pendingSync=false + SyncQueueManager.removeByEntity,
 *                       иначе следующий sync перезапишет сервер локальным значением.
 */
@Singleton
class SyncConflictApplier @Inject constructor(
    private val eventParticipantDao: EventParticipantDao,
    private val bookRepository: BookRepository,
    private val customGroupDao: CustomGroupDao,
    private val userRepository: UserRepository,
    private val syncQueueManager: SyncQueueManager,
    private val eventRepository: EventRepository,
) {
    companion object { private const val TAG = "SyncConflictApplier" }
    private val gson = Gson()

    suspend fun apply(conflict: SyncConflictEntity, chooseLocal: Boolean) {
        try {
            when (conflict.entityType) {
                "event_participant" -> applyEventParticipant(conflict, chooseLocal)
                "book" -> applyBook(conflict, chooseLocal)
                "custom_group" -> applyCustomGroup(conflict, chooseLocal)
                "user" -> applyUser(conflict, chooseLocal)
                else -> Log.w(TAG, "apply: entityType=${conflict.entityType} не поддержан")
            }
            if (!chooseLocal) {
                syncQueueManager.removeByEntity(conflict.entityType, conflict.entityId)
                Log.d(TAG, "apply: queue cleared for ${conflict.entityType}/${conflict.entityId}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "apply failed for ${conflict.entityType}/${conflict.entityId}", e)
        }
    }

    private suspend fun applyEventParticipant(conflict: SyncConflictEntity, chooseLocal: Boolean) {
        val json = if (chooseLocal) conflict.localValue else conflict.serverValue
        val participant = gson.fromJson(json, EventParticipantEntity::class.java) ?: return
        eventParticipantDao.upsert(participant.copy(pendingSync = chooseLocal))
        if (chooseLocal) {
            // Баг 0.1: при chooseLocal нужно поставить в очередь, иначе pull затрёт.
            // clubId в entity нет — берём из события.
            try {
                val event = eventRepository.getEvent(participant.eventId)
                if (event != null && !event.clubId.isNullOrBlank()) {
                    syncQueueManager.enqueue(
                        "event_participant", participant.id, "UPDATE",
                        mapOf(
                            "id" to participant.id,
                            "eventId" to participant.eventId,
                            "userId" to participant.userId,
                            "status" to participant.status,
                            "invitedAt" to participant.invitedAt.time,
                            "respondedAt" to participant.respondedAt?.time,
                            "attendedAt" to participant.attendedAt?.time,
                            "clubId" to event.clubId,
                            "pendingSync" to false,
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        Log.d(TAG, "applyEventParticipant: ${conflict.entityId} chooseLocal=$chooseLocal")
    }

    private suspend fun applyBook(conflict: SyncConflictEntity, chooseLocal: Boolean) {
        val json = if (chooseLocal) conflict.localValue else conflict.serverValue
        val book = gson.fromJson(json, BookEntity::class.java) ?: return
        bookRepository.upsert(book.copy(pendingSync = chooseLocal))
        if (chooseLocal) {
            // Баг 0.1: pushClubBook ставит в sync_queue (book-ветка), иначе pull затрёт.
            try { bookRepository.pushClubBook(book.copy(pendingSync = true)) } catch (_: Exception) {}
        }
        Log.d(TAG, "applyBook: ${conflict.entityId} chooseLocal=$chooseLocal")
    }

    private suspend fun applyCustomGroup(conflict: SyncConflictEntity, chooseLocal: Boolean) {
        val json = if (chooseLocal) conflict.localValue else conflict.serverValue
        val group = gson.fromJson(json, CustomGroupEntity::class.java) ?: return
        customGroupDao.upsert(group.copy(pendingSync = chooseLocal))
        if (chooseLocal) {
            // Баг 0.1: enqueue custom_group, иначе pull затрёт.
            try {
                syncQueueManager.enqueue(
                    "custom_group", group.id, "upsert", group.copy(pendingSync = false)
                )
            } catch (_: Exception) {}
        }
        Log.d(TAG, "applyCustomGroup: ${conflict.entityId} chooseLocal=$chooseLocal")
    }

    private suspend fun applyUser(conflict: SyncConflictEntity, chooseLocal: Boolean) {
        val json = if (chooseLocal) conflict.localValue else conflict.serverValue
        val user = gson.fromJson(json, UserEntity::class.java) ?: return
        if (chooseLocal) {
            // update() из UserRepository ставит в очередь — это то, что нужно для chooseLocal.
            userRepository.update(user)
        } else {
            // updateLocalOnly() — без очереди, дальше removeByEntity очистит pending.
            userRepository.updateLocalOnly(user)
        }
        Log.d(TAG, "applyUser: ${conflict.entityId} chooseLocal=$chooseLocal")
    }
}
