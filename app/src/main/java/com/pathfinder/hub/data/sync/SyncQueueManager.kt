package com.pathfinder.hub.data.sync

import android.util.Log
import com.google.gson.Gson
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.dao.SyncDao
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import com.pathfinder.hub.data.local.entity.offline.SyncStatusEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncQueueManager @Inject constructor(
    private val syncDao: SyncDao,
    private val sessionManager: SessionManager
) {

    private val gson = Gson()

    suspend fun enqueue(
        entityType: String,
        entityId: String,
        operation: String,
        payload: Any,
        priority: String = "normal"
    ) {
        val userId = sessionManager.getUserId() ?: return
        val item = SyncQueueItemEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            operation = operation,
            entityType = entityType,
            entityId = entityId,
            payload = gson.toJson(payload),
            status = "pending",
            retryCount = 0,
            lastAttemptAt = null,
            errorMessage = null,
            createdAt = Date(),
            priority = priority
        )
        syncDao.upsertItem(item)
        Log.d(TAG, "Enqueue: $operation $entityType/$entityId")
    }

    suspend fun getNextBatch(limit: Int = 50): List<SyncQueueItemEntity> {
        return syncDao.getNextBatch(limit)
    }

    suspend fun updateStatus(
        id: String,
        status: String,
        retryCount: Int = 0,
        errorMessage: String? = null
    ) {
        syncDao.updateItemStatus(
            id = id,
            status = status,
            retry = retryCount,
            at = System.currentTimeMillis(),
            err = errorMessage
        )
    }

    suspend fun remove(id: String) {
        syncDao.deleteItem(id)
    }

    suspend fun updateSyncStatus(
        isOnline: Boolean = true,
        isSyncing: Boolean = false,
        queueSize: Int = 0,
        lastError: String? = null
    ) {
        syncDao.upsertStatus(
            SyncStatusEntity(
                id = 1,
                isOnline = isOnline,
                isSyncing = isSyncing,
                queueSize = queueSize,
                lastSyncAt = if (isSyncing) null else Date(),
                lastError = lastError,
                conflictsCount = 0
            )
        )
    }

    companion object {
        private const val TAG = "SyncQueue"
    }
}
