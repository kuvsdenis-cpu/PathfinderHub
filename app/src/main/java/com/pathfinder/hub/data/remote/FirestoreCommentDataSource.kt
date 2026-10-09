package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pathfinder.hub.data.local.entity.planning.CommentEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для комментариев и сообщений чата.
 *
 * Коллекция: clubs/{clubId}/comments/{commentId}
 *
 * Чтение — через snapshot listener (реальное время).
 * Запись — через set().
 *
 * Правила Firestore (firestore.rules):
 *   match /clubs/{clubId}/comments/{commentId} {
 *     allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
 *     allow create: if isSignedIn() && request.resource.data.authorId == uid();
 *     allow update, delete: if isSignedIn() && (isOwnClub(clubId) && (isDirector() || isModerator(clubId)));
 *   }
 */
@Singleton
class FirestoreCommentDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreCommentDS"
    }

    private fun commentsRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection("comments")

    /**
     * Наблюдает за опубликованными комментариями targetType/targetId в реальном времени.
     *
     * При ошибке (нет сети, нет индекса, правила) — отдаёт пустой список и закрывает flow,
     * чтобы UI не зависал в loading. Наружу ошибка не пробрасывается: UI получит emptyList,
     * а повторную подписку делает ViewModel.
     *
     * @param clubId  клуб
     * @param targetType "club_chat" | "event" | "task" | ...
     * @param targetId   id объекта (для чата = clubId, для события = eventId)
     */
    fun observeApprovedComments(
        clubId: String,
        targetType: String,
        targetId: String,
    ): Flow<List<CommentEntity>> = callbackFlow {
        if (clubId.isBlank()) {
            Log.w(TAG, "observeApprovedComments: clubId пустой")
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val query = commentsRef(clubId)
            .whereEqualTo("targetType", targetType)
            .whereEqualTo("targetId", targetId)
            .whereEqualTo("status", "published")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "snapshot listener failed", error)
                trySend(emptyList())
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                try {
                    mapToEntity(doc.id, doc.data ?: emptyMap())
                } catch (e: Exception) {
                    Log.e(TAG, "mapToEntity failed for ${doc.id}", e)
                    null
                }
            } ?: emptyList()
            trySend(list)
        }

        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeApprovedComments flow error", e)
        emit(emptyList())
    }

    /**
     * Очередь ручной модерации — читаем напрямую из Firestore.
     *
     * Раньше ModerationScreen читал из Room, но комментарии пишутся
     * сразу в Firestore и в Room-кэш автора не попадают. Из-за этого
     * директор не видел флаги с чужих устройств.
     *
     * Требуется составной индекс:
     *   collection: comments
     *   fields:     status ASC, createdAt DESC
     * Firestore подскажет ссылку в логах при первом запросе.
     */
    fun observePendingComments(clubId: String): Flow<List<CommentEntity>> = callbackFlow {
        if (clubId.isBlank()) {
            Log.w(TAG, "observePendingComments: clubId пустой")
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = commentsRef(clubId)
            .whereIn("status", listOf("pending_review", "draft", "flagged"))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "pendingComments listener failed", error)
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        mapToEntity(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "mapToEntity(pending) failed ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observePendingComments flow error", e)
        emit(emptyList())
    }

    /**
     * Записывает комментарий в Firestore.
     * Идемпотентно: если документ уже есть — перезапишет.
     */
    suspend fun sendComment(comment: CommentEntity): Result<Unit> = try {
        if (comment.clubId.isBlank()) {
            throw IllegalArgumentException("clubId пустой — нельзя записать в Firestore")
        }
        commentsRef(comment.clubId)
            .document(comment.id)
            .set(mapFromEntity(comment))
            .await()
        Log.d(TAG, "sendComment OK: ${comment.clubId}/${comment.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "sendComment FAILED: ${comment.clubId}/${comment.id}", e)
        Result.failure(e)
    }

    /**
     * Удаляет комментарий из Firestore.
     */
    suspend fun deleteComment(clubId: String, commentId: String): Result<Unit> = try {
        commentsRef(clubId).document(commentId).delete().await()
        Log.d(TAG, "deleteComment OK: $clubId/$commentId")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "deleteComment FAILED: $clubId/$commentId", e)
        Result.failure(e)
    }

    // ==================== МАППИНГ ====================

    private fun mapToEntity(id: String, data: Map<String, Any?>): CommentEntity {
        return CommentEntity(
            id = id,
            targetType = data["targetType"] as? String ?: "",
            targetId = data["targetId"] as? String ?: "",
            clubId = data["clubId"] as? String ?: "",
            authorId = data["authorId"] as? String ?: "",
            text = data["text"] as? String ?: "",
            attachments = (data["attachments"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            status = data["status"] as? String ?: "published",
            autoModerationResult = data["autoModerationResult"] as? String ?: "skipped",
            publishedAt = parseDate(data["publishedAt"]),
            reviewDeadline = parseDate(data["reviewDeadline"]),
            moderatedBy = data["moderatedBy"] as? String,
            moderatedAt = parseDate(data["moderatedAt"]),
            hiddenReasonCode = data["hiddenReasonCode"] as? String,
            hiddenReasonNote = data["hiddenReasonNote"] as? String,
            createdAt = parseDate(data["createdAt"]) ?: Date(),
            pendingSync = false
        )
    }

    private fun mapFromEntity(c: CommentEntity): Map<String, Any?> {
        return mapOf(
            "id" to c.id,
            "targetType" to c.targetType,
            "targetId" to c.targetId,
            "clubId" to c.clubId,
            "authorId" to c.authorId,
            "text" to c.text,
            "attachments" to c.attachments,
            "status" to c.status,
            "autoModerationResult" to c.autoModerationResult,
            "publishedAt" to c.publishedAt?.let { Timestamp(it) },
            "reviewDeadline" to c.reviewDeadline?.let { Timestamp(it) },
            "moderatedBy" to c.moderatedBy,
            "moderatedAt" to c.moderatedAt?.let { Timestamp(it) },
            "hiddenReasonCode" to c.hiddenReasonCode,
            "hiddenReasonNote" to c.hiddenReasonNote,
            "createdAt" to Timestamp(c.createdAt),
            "pendingSync" to false
        )
    }

    private fun parseDate(value: Any?): Date? {
        return when (value) {
            null -> null
            is Date -> value
            is Timestamp -> value.toDate()
            is Long -> Date(value)
            is String -> {
                val formats = listOf(
                    "MMM d, yyyy HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd"
                )
                for (fmt in formats) {
                    try {
                        return java.text.SimpleDateFormat(fmt, java.util.Locale.ENGLISH).parse(value)
                    } catch (_: Exception) {
                        // пробуем следующий
                    }
                }
                null
            }
            else -> null
        }
    }
}