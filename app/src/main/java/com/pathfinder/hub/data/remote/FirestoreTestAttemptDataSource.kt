package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для попыток тестов.
 *
 * Путь:
 *   users/{userId}/testAttempts/{attemptId}
 *
 * Только чтение (pull). Запись — через SyncWorker (push).
 * Rules: users/{userId}/testAttempts — read для staff клуба (уже готовы).
 *
 * Возвращает RemoteSnapshot (см. FirestoreTestDataSource) — для tombstone-логики
 * в pull: если snapshot из кэша или с ошибкой — нельзя удалять локальные данные.
 */
@Singleton
class FirestoreTestAttemptDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreAttemptDS"
    }

    private fun attemptsRef(userId: String) =
        firestore.collection("users").document(userId).collection("testAttempts")

    fun observeTestAttempts(userId: String): Flow<RemoteSnapshot<TestAttemptEntity>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(RemoteSnapshot(emptyList(), fromCache = false, hasError = false))
            awaitClose { }; return@callbackFlow
        }

        val registration = attemptsRef(userId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "testAttempts listener failed", err)
                    trySend(RemoteSnapshot(emptyList(), fromCache = false, hasError = true))
                    return@addSnapshotListener
                }
                val fromCache = snap?.metadata?.isFromCache ?: true
                val list = snap?.documents?.mapNotNull { doc ->
                    try { mapAttempt(doc.id, doc.data ?: emptyMap(), userId) }
                    catch (e: Exception) { Log.e(TAG, "map attempt failed ${doc.id}", e); null }
                } ?: emptyList()
                trySend(RemoteSnapshot(list, fromCache, hasError = false))
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeTestAttempts flow error", e)
        emit(RemoteSnapshot(emptyList(), fromCache = false, hasError = true))
    }

    private fun mapAttempt(
        id: String,
        d: Map<String, Any?>,
        userId: String,
    ): TestAttemptEntity = TestAttemptEntity(
        id = id,
        userId = (d["userId"] as? String) ?: userId,
        honorId = d["honorId"] as? String ?: "",
        testId = d["testId"] as? String,
        score = (d["score"] as? Number)?.toInt() ?: 0,
        passed = d["passed"] as? Boolean ?: false,
        attemptNumber = (d["attemptNumber"] as? Number)?.toInt() ?: 1,
        startedAt = parseDate(d["startedAt"]) ?: Date(),
        completedAt = parseDate(d["completedAt"]) ?: Date(),
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
