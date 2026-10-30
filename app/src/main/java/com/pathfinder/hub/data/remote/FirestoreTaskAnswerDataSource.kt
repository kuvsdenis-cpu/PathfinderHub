package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для ответов на проверочные задания.
 *
 * Путь: clubs/{clubId}/requirementTaskAnswers/{userId}_{taskId}
 *
 * - Следопыт читает свои через query whereEqualTo("userId", uid).
 * - Наставник/директор читает очередь через whereEqualTo("status", "submitted").
 * - Один источник данных, без дублирования.
 */
@Singleton
class FirestoreTaskAnswerDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreTaskAnswerDS"
        private const val COLLECTION = "requirementTaskAnswers"

        fun docId(userId: String, taskId: String) = "${userId}_${taskId}"
    }

    private fun answersRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COLLECTION)

    fun observeMyAnswers(
        clubId: String,
        userId: String,
    ): Flow<List<RequirementTaskAnswerEntity>> = callbackFlow {
        if (clubId.isBlank() || userId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = answersRef(clubId)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "myAnswers listener failed", error)
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        map(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "answer map failed ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeMyAnswers flow error", e)
        emit(emptyList())
    }

    fun observeMyAnswer(
        clubId: String,
        userId: String,
        taskId: String,
    ): Flow<RequirementTaskAnswerEntity?> = callbackFlow {
        if (clubId.isBlank() || userId.isBlank() || taskId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val registration = answersRef(clubId)
            .document(docId(userId, taskId))
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.w(TAG, "PERMISSION_DENIED, emit empty (flow continues)", error)
                        trySend(null)
                        return@addSnapshotListener
                    }
                    Log.e(TAG, "myAnswer listener failed", error)
                    trySend(null)
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.takeIf { it.exists() }?.let { map(it.id, it.data ?: emptyMap()) })
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeMyAnswer flow error", e)
        emit(null)
    }

    fun observeReviewQueue(
        clubId: String,
    ): Flow<List<RequirementTaskAnswerEntity>> = callbackFlow {
        if (clubId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = answersRef(clubId)
            .whereEqualTo("status", RequirementTaskAnswerEntity.STATUS_SUBMITTED)
            .orderBy("submittedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "reviewQueue listener failed", error)
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        map(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "queue map failed ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeReviewQueue flow error", e)
        emit(emptyList())
    }

    suspend fun upsertAnswer(answer: RequirementTaskAnswerEntity): Result<Unit> = try {
        if (answer.clubId.isBlank() || answer.userId.isBlank() || answer.taskId.isBlank()) {
            throw IllegalArgumentException("clubId/userId/taskId пустые")
        }
        answersRef(answer.clubId)
            .document(docId(answer.userId, answer.taskId))
            .set(toMap(answer))
            .await()
        Log.d(TAG, "upsertAnswer OK: ${answer.userId}_${answer.taskId}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "upsertAnswer FAIL", e)
        Result.failure(e)
    }

    suspend fun reviewAnswer(answer: RequirementTaskAnswerEntity): Result<Unit> = try {
        answersRef(answer.clubId)
            .document(docId(answer.userId, answer.taskId))
            .set(toMap(answer))
            .await()
        Log.d(TAG, "reviewAnswer OK: ${answer.userId}_${answer.taskId}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "reviewAnswer FAIL", e)
        Result.failure(e)
    }

    /**
     * Один раз тянет все ответы клуба. Для staff — все (rules разрешают),
     * для тина — только свои (rules режут на read). Используется в SyncWorker,
     * чтобы второе устройство staff видело ответы тинов, а тин — статусы
     * approve/reject, выставленные на другом устройстве.
     */
    suspend fun pullAnswers(clubId: String): List<RequirementTaskAnswerEntity> = try {
        answersRef(clubId).get().await().documents.mapNotNull { doc ->
            try {
                map(doc.id, doc.data ?: emptyMap())
            } catch (e: Exception) {
                Log.e(TAG, "pullAnswers map failed ${doc.id}", e)
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "pullAnswers FAIL: $clubId", e)
        emptyList()
    }

    // ==================== МАППИНГ ====================

    private fun map(id: String, d: Map<String, Any?>): RequirementTaskAnswerEntity {
        return RequirementTaskAnswerEntity(
            id = id,
            taskId = d["taskId"] as? String ?: "",
            requirementId = d["requirementId"] as? String ?: "",
            userId = d["userId"] as? String ?: "",
            clubId = d["clubId"] as? String ?: "",
            answer = d["answer"] as? String ?: "",
            attachments = (d["attachments"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            status = d["status"] as? String ?: RequirementTaskAnswerEntity.STATUS_DRAFT,
            autoChecked = d["autoChecked"] as? Boolean ?: false,
            submittedAt = parseDate(d["submittedAt"]),
            reviewedBy = d["reviewedBy"] as? String,
            reviewedAt = parseDate(d["reviewedAt"]),
            reviewComment = d["reviewComment"] as? String,
            createdAt = parseDate(d["createdAt"]) ?: Date(),
            updatedAt = parseDate(d["updatedAt"]) ?: Date(),
            pendingSync = false
        )
    }

    private fun toMap(a: RequirementTaskAnswerEntity): Map<String, Any?> = mapOf(
        "id" to a.id,
        "taskId" to a.taskId,
        "requirementId" to a.requirementId,
        "userId" to a.userId,
        "clubId" to a.clubId,
        "answer" to a.answer,
        "attachments" to a.attachments,
        "status" to a.status,
        "autoChecked" to a.autoChecked,
        "submittedAt" to a.submittedAt?.let { Timestamp(it) },
        "reviewedBy" to a.reviewedBy,
        "reviewedAt" to a.reviewedAt?.let { Timestamp(it) },
        "reviewComment" to a.reviewComment,
        "createdAt" to Timestamp(a.createdAt),
        "updatedAt" to Timestamp(a.updatedAt)
    )

    private fun parseDate(v: Any?): Date? = when (v) {
        null -> null
        is Date -> v
        is Timestamp -> v.toDate()
        is Long -> Date(v)
        else -> null
    }
}
