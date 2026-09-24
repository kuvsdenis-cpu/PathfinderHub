package com.pathfinder.hub.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncQueue: SyncQueueManager,
    private val firestore: FirestoreSyncService
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        Log.d(TAG, "Start sync...")
        val batch = syncQueue.getNextBatch(50)
        if (batch.isEmpty()) {
            Log.d(TAG, "Queue empty")
            return Result.success()
        }

        syncQueue.updateSyncStatus(isSyncing = true, queueSize = batch.size)

        var success = 0
        var errors = 0
        for (item in batch) {
            val ok = processItem(item)
            if (ok) {
                syncQueue.remove(item.id)
                success++
            } else {
                errors++
                if (item.retryCount >= 5) {
                    syncQueue.updateStatus(
                        id = item.id,
                        status = "failed",
                        retryCount = item.retryCount,
                        errorMessage = "Max retries"
                    )
                }
            }
        }

        syncQueue.updateSyncStatus(
            isSyncing = false,
            queueSize = 0,
            lastError = if (errors > 0) "Errors: $errors" else null
        )
        Log.d(TAG, "Sync done: success=$success, errors=$errors")
        return Result.success()
    }

    private suspend fun processItem(item: SyncQueueItemEntity): Boolean {
        return try {
            @Suppress("UNCHECKED_CAST")
            val payload = gson.fromJson(item.payload, Map::class.java) as? Map<String, Any?>
                ?: emptyMap()

            val collectionPath = when (item.entityType) {
                "user" -> "users"
                "club" -> "clubs"
                "event" -> "clubs/${payload["clubId"]}/events"
                "task" -> "clubs/${payload["clubId"]}/tasks"
                "level_progress" -> "users/${item.userId}/levelProgress"
                "honor_progress" -> "users/${item.userId}/honorProgress"
                "honor_completion" -> "users/${item.userId}/honorCompletions"
                "level_completion" -> "users/${item.userId}/levelCompletions"
                "test_attempt" -> "users/${item.userId}/testAttempts"
                "open_answer_review" -> "users/${item.userId}/openAnswerReviews"
                "honor_draft" -> "clubs/${payload["clubId"]}/honorDrafts"
                "book_report" -> "users/${item.userId}/bookReports"
                "verse_progress" -> "users/${item.userId}/verseProgress"
                "leader_checklist" -> "users/${item.userId}/leaderChecklists"
                else -> {
                    Log.w(TAG, "Unknown entityType: ${item.entityType}")
                    return false
                }
            }

            val result: Boolean = when (item.operation.lowercase()) {
                "create", "update" -> {
                    val r = firestore.pushDocument(
                        collection = collectionPath,
                        documentId = item.entityId,
                        data = payload
                    )
                    r.isSuccess
                }
                "delete" -> {
                    val r = firestore.deleteDocument(
                        collection = collectionPath,
                        documentId = item.entityId
                    )
                    r.isSuccess
                }
                else -> {
                    Log.w(TAG, "Unknown operation: ${item.operation}")
                    false
                }
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Process error ${item.id}", e)
            syncQueue.updateStatus(
                id = item.id,
                status = "pending",
                retryCount = item.retryCount + 1,
                errorMessage = e.message
            )
            false
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "firestore_sync"
    }
}