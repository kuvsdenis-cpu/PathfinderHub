package com.pathfinder.hub.data.repository

import android.util.Log
import com.google.gson.Gson
import com.pathfinder.hub.data.local.dao.EventParticipantDao
import com.pathfinder.hub.data.local.entity.offline.SyncConflictEntity
import com.pathfinder.hub.data.local.entity.planning.EventParticipantEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Применяет решение пользователя к фактической сущности.
 *
 * MVP: поддержан event_participant.
 *
 * TODO (next iteration):
 *   - "book" — применить через BookRepository.upsert(local.copy(pendingSync=true/false)).
 *   - "custom_group" — через CustomGroupDao.upsert(...).
 *   - "user" — через UserRepository.update(...).
 *   - При chooseLocal=false необходимо также очищать запись из sync_queue,
 *     иначе SyncWorker отправит локальное значение при следующей синхронизации
 *     и перезапишет сервер. Требует инжекта SyncQueueManager + новый метод в SyncDao.
 */
@Singleton
class SyncConflictApplier @Inject constructor(
    private val eventParticipantDao: EventParticipantDao,
) {
    companion object { private const val TAG = "SyncConflictApplier" }
    private val gson = Gson()

    suspend fun apply(conflict: SyncConflictEntity, chooseLocal: Boolean) {
        try {
            when (conflict.entityType) {
                "event_participant" -> applyEventParticipant(conflict, chooseLocal)
                else -> Log.w(TAG, "apply: entityType=${conflict.entityType} пока не поддержан (TODO)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "apply failed for ${conflict.entityType}/${conflict.entityId}", e)
        }
    }

    private suspend fun applyEventParticipant(conflict: SyncConflictEntity, chooseLocal: Boolean) {
        val json = if (chooseLocal) conflict.localValue else conflict.serverValue
        val participant = gson.fromJson(json, EventParticipantEntity::class.java) ?: return
        // chooseLocal=true: pendingSync=true — SyncWorker отправит при следующей синхронизации.
        // chooseLocal=false: pendingSync=false — принимаем серверное значение локально.
        eventParticipantDao.upsert(participant.copy(pendingSync = chooseLocal))
        Log.d(TAG, "applyEventParticipant: ${conflict.entityId} chooseLocal=$chooseLocal")
    }
}
