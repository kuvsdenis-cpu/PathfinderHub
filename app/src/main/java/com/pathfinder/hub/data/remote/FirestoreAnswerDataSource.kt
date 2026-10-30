package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreAnswerDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreAnswerDS"
        const val COLLECTION = "requirementTaskAnswers"
        const val STATUS_SUBMITTED = "submitted"
    }

    private fun answersRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COLLECTION)

    fun observePendingAnswers(clubId: String): Flow<List<RequirementTaskAnswerEntity>> =
        callbackFlow {
            if (clubId.isBlank()) {
                trySend(emptyList()); awaitClose { }; return@callbackFlow
            }
            val reg = answersRef(clubId)
                .whereEqualTo("status", STATUS_SUBMITTED)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.e(TAG, "listener failed", err)
                        trySend(emptyList()); close(err); return@addSnapshotListener
                    }
                    val list = snap?.documents?.mapNotNull { d ->
                        try { mapToEntity(d.id, d.data ?: emptyMap()) }
                        catch (e: Exception) { Log.e(TAG, "map ${d.id}", e); null }
                    } ?: emptyList()
                    trySend(list)
                }
            awaitClose { reg.remove() }
        }.catch { e -> Log.e(TAG, "flow error", e); emit(emptyList()) }

    suspend fun updateStatus(
        clubId: String, answerId: String, status: String,
        reviewedBy: String, comment: String?,
    ): Result<Unit> = try {
        answersRef(clubId).document(answerId).update(
            mapOf(
                "status" to status,
                "reviewedBy" to reviewedBy,
                "reviewedAt" to Timestamp(Date()),
                "reviewComment" to comment,
                "pendingSync" to false,
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "updateStatus $answerId", e); Result.failure(e)
    }

    private fun mapToEntity(id: String, d: Map<String, Any?>): RequirementTaskAnswerEntity =
        RequirementTaskAnswerEntity(
            id = id,
            taskId = d["taskId"] as? String ?: "",
            requirementId = d["requirementId"] as? String ?: "",
            userId = d["userId"] as? String ?: "",
            clubId = d["clubId"] as? String ?: "",
            answer = d["answer"] as? String ?: "",
            attachments = (d["attachments"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            status = d["status"] as? String ?: "draft",
            autoChecked = d["autoChecked"] as? Boolean ?: false,
            submittedAt = parseDate(d["submittedAt"]),
            reviewedBy = d["reviewedBy"] as? String,
            reviewedAt = parseDate(d["reviewedAt"]),
            reviewComment = d["reviewComment"] as? String,
            createdAt = parseDate(d["createdAt"]) ?: Date(),
            updatedAt = parseDate(d["updatedAt"]) ?: Date(),
            pendingSync = false,
        )

    private fun parseDate(v: Any?): Date? = when (v) {
        null -> null
        is Date -> v
        is Timestamp -> v.toDate()
        is Long -> Date(v)
        else -> null
    }
}
