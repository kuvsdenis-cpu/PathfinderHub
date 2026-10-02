package com.pathfinder.hub.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.gson.Gson
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeoutOrNull

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncQueue: SyncQueueManager,
    private val firestore: FirestoreSyncService
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        Log.d(TAG, "▶️ Start sync...")

        val batch = syncQueue.getNextBatch(50)
        if (batch.isEmpty()) {
            Log.d(TAG, "✓ Queue empty")
            return Result.success()
        }

        syncQueue.updateSyncStatus(isSyncing = true, queueSize = batch.size)

        var success = 0
        var errors = 0
        var permanentFailures = 0

        for (item in batch) {
            // Ограничиваем время на один item — 30 секунд
            val result = withTimeoutOrNull(30_000L) {
                processItem(item)
            }

            when (result) {
                ProcessResult.SUCCESS -> {
                    syncQueue.remove(item.id)
                    success++
                }
                ProcessResult.RETRY -> {
                    errors++
                    val newRetry = item.retryCount + 1
                    if (newRetry >= MAX_RETRIES) {
                        syncQueue.updateStatus(
                            id = item.id,
                            status = "failed",
                            retryCount = newRetry,
                            errorMessage = "Max retries exceeded"
                        )
                        permanentFailures++
                    } else {
                        syncQueue.updateStatus(
                            id = item.id,
                            status = "pending",
                            retryCount = newRetry,
                            errorMessage = "Retry $newRetry"
                        )
                    }
                }
                ProcessResult.PERMANENT_FAILURE -> {
                    // Не retry — например, PERMISSION_DENIED
                    syncQueue.updateStatus(
                        id = item.id,
                        status = "failed",
                        retryCount = item.retryCount,
                        errorMessage = "Permanent failure"
                    )
                    permanentFailures++
                }
                null -> {
                    // Timeout
                    Log.w(TAG, "⏱ Timeout on item ${item.id}")
                    syncQueue.updateStatus(
                        id = item.id,
                        status = "pending",
                        retryCount = item.retryCount + 1,
                        errorMessage = "Timeout"
                    )
                    errors++
                }
            }
        }

        syncQueue.updateSyncStatus(
            isSyncing = false,
            queueSize = 0,
            lastError = when {
                permanentFailures > 0 -> "Permanent: $permanentFailures, errors: $errors"
                errors > 0 -> "Errors: $errors"
                else -> null
            }
        )

        Log.d(TAG, "✓ Sync done: success=$success, errors=$errors, permanent=$permanentFailures")
        return Result.success()
    }

    private enum class ProcessResult {
        SUCCESS,
        RETRY,
        PERMANENT_FAILURE
    }

    private suspend fun processItem(item: SyncQueueItemEntity): ProcessResult {
        return try {
            @Suppress("UNCHECKED_CAST")
            val payload = gson.fromJson(item.payload, Map::class.java) as? Map<String, Any?>
                ?: emptyMap()

            val collectionPath = resolveCollectionPath(item, payload)
            if (collectionPath == null) {
                Log.w(TAG, "❌ Unknown entityType: ${item.entityType}")
                return ProcessResult.PERMANENT_FAILURE
            }

            val result = when (item.operation.lowercase()) {
                "create", "update" -> firestore.pushDocument(
                    collection = collectionPath,
                    documentId = item.entityId,
                    data = payload
                )
                "delete" -> firestore.deleteDocument(
                    collection = collectionPath,
                    documentId = item.entityId
                )
                else -> {
                    Log.w(TAG, "❌ Unknown operation: ${item.operation}")
                    return ProcessResult.PERMANENT_FAILURE
                }
            }

            if (result.isSuccess) {
                ProcessResult.SUCCESS
            } else {
                val error = result.exceptionOrNull()
                classifyError(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Process error ${item.id}", e)
            classifyError(e)
        }
    }

    /**
     * Классификация ошибки: retry или permanent failure.
     */
    private fun classifyError(error: Throwable?): ProcessResult {
        return when (error) {
            is FirebaseFirestoreException -> when (error.code) {
                // Неверные права — не retry
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    Log.e(TAG, "🔒 PERMISSION_DENIED — не retry")
                    ProcessResult.PERMANENT_FAILURE
                }
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                    Log.e(TAG, "🔒 UNAUTHENTICATED — не retry")
                    ProcessResult.PERMANENT_FAILURE
                }
                FirebaseFirestoreException.Code.INVALID_ARGUMENT -> {
                    Log.e(TAG, "❌ INVALID_ARGUMENT — не retry")
                    ProcessResult.PERMANENT_FAILURE
                }
                FirebaseFirestoreException.Code.NOT_FOUND -> {
                    // Документ не найден при delete — уже удалён, считаем success
                    Log.d(TAG, "ℹ️ NOT_FOUND — считаем success")
                    ProcessResult.SUCCESS
                }
                // Сетевые — retry
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> {
                    Log.w(TAG, "🌐 ${error.code} — retry")
                    ProcessResult.RETRY
                }
                else -> {
                    Log.w(TAG, "⚠️ ${error.code} — retry")
                    ProcessResult.RETRY
                }
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown error — retry", error)
                ProcessResult.RETRY
            }
        }
    }

    /**
     * Маппинг entityType → Firestore collection path.
     * Возвращает null, если entityType неизвестен.
     */
    private fun resolveCollectionPath(
        item: SyncQueueItemEntity,
        payload: Map<String, Any?>
    ): String? {
        // userId для subcollection'ов
        val userId = item.userId

        // clubId из payload или из userId (для клубных сущностей)
        val clubId = payload["clubId"] as? String

        return when (item.entityType) {
            // ---------- Глобальные ----------
            "user" -> "users"
            "club" -> "clubs"

            // ---------- Клубные ----------
            "event" -> if (clubId != null) "clubs/$clubId/events" else null
            "task" -> if (clubId != null) "clubs/$clubId/tasks" else null
            "assignment" -> if (clubId != null) "clubs/$clubId/assignments" else null
            "comment" -> if (clubId != null) "clubs/$clubId/comments" else null
            "moderation_request" -> if (clubId != null) "clubs/$clubId/moderationRequests" else null
            "moderation_history" -> if (clubId != null) "clubs/$clubId/moderationHistory" else null
            "appeal" -> if (clubId != null) "clubs/$clubId/appeals" else null
            "honor_draft" -> if (clubId != null) "clubs/$clubId/honorDrafts" else null
            "report" -> if (clubId != null) "clubs/$clubId/reports" else null

            // ---------- Прогресс пользователя ----------
            "level_progress" -> "users/$userId/levelProgress"
            "honor_progress" -> "users/$userId/honorProgress"
            "level_completion" -> "users/$userId/levelCompletions"
            "honor_completion" -> "users/$userId/honorCompletions"
            "test_attempt" -> "users/$userId/testAttempts"
            "open_answer_review" -> "users/$userId/openAnswerReviews"
            "book_report" -> "users/$userId/bookReports"
            "verse_progress" -> "users/$userId/verseProgress"
            "leader_checklist" -> "users/$userId/leaderChecklists"
            "notification" -> "users/$userId/notifications"

            else -> null
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "firestore_sync"
        private const val MAX_RETRIES = 5
    }
}