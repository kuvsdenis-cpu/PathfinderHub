package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pathfinder.hub.data.local.entity.learning.TestEntity
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над snapshot: items + метаданные.
 * Нужна для tombstone-логики в pull: если snapshot из кэша или с ошибкой —
 * нельзя удалять локальные данные, которых нет в items.
 */
data class RemoteSnapshot<T>(
    val items: List<T>,
    val fromCache: Boolean,
    val hasError: Boolean,
)

/**
 * Firestore-источник для клубных тестов.
 *
 * Путь:
 *   clubs/{clubId}/tests/{testId}
 *   clubs/{clubId}/tests/{testId}/questions/{questionId}
 *
 * Только чтение (pull). Запись — через SyncWorker (push).
 */
@Singleton
class FirestoreTestDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreTestDS"
    }

    private fun testsRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection("tests")

    private fun questionsRef(clubId: String, testId: String) =
        firestore.collection("clubs").document(clubId)
            .collection("tests").document(testId).collection("questions")

    // ---------- Тесты ----------

    fun observeClubTests(clubId: String): Flow<RemoteSnapshot<TestEntity>> = callbackFlow {
        if (clubId.isBlank()) {
            trySend(RemoteSnapshot(emptyList(), fromCache = false, hasError = false))
            awaitClose { }; return@callbackFlow
        }

        val registration = testsRef(clubId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "clubTests listener failed", err)
                    trySend(RemoteSnapshot(emptyList(), fromCache = false, hasError = true))
                    return@addSnapshotListener
                }
                val fromCache = snap?.metadata?.isFromCache ?: true
                val list = snap?.documents?.mapNotNull { doc ->
                    try { mapTest(doc.id, doc.data ?: emptyMap(), clubId) }
                    catch (e: Exception) { Log.e(TAG, "map test failed ${doc.id}", e); null }
                } ?: emptyList()
                trySend(RemoteSnapshot(list, fromCache, hasError = false))
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeClubTests flow error", e)
        emit(RemoteSnapshot(emptyList(), fromCache = false, hasError = true))
    }

    // ---------- Вопросы ----------

    fun observeTestQuestions(
        clubId: String,
        testId: String,
    ): Flow<RemoteSnapshot<TestQuestionEntity>> = callbackFlow {
        if (clubId.isBlank() || testId.isBlank()) {
            trySend(RemoteSnapshot(emptyList(), fromCache = false, hasError = false))
            awaitClose { }; return@callbackFlow
        }

        val registration = questionsRef(clubId, testId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "testQuestions listener failed", err)
                    trySend(RemoteSnapshot(emptyList(), fromCache = false, hasError = true))
                    return@addSnapshotListener
                }
                val fromCache = snap?.metadata?.isFromCache ?: true
                val list = snap?.documents?.mapNotNull { doc ->
                    try { mapQuestion(doc.id, doc.data ?: emptyMap(), testId, clubId) }
                    catch (e: Exception) { Log.e(TAG, "map question failed ${doc.id}", e); null }
                } ?: emptyList()
                trySend(RemoteSnapshot(list, fromCache, hasError = false))
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeTestQuestions flow error", e)
        emit(RemoteSnapshot(emptyList(), fromCache = false, hasError = true))
    }

    // ---------- Маппинг ----------

    private fun mapTest(id: String, d: Map<String, Any?>, clubId: String): TestEntity =
        TestEntity(
            id = id,
            clubId = (d["clubId"] as? String) ?: clubId,
            scope = d["scope"] as? String ?: "standalone",
            scopeId = d["scopeId"] as? String,
            title = d["title"] as? String ?: "",
            description = d["description"] as? String ?: "",
            passScore = (d["passScore"] as? Number)?.toInt() ?: 90,
            archivedAt = parseDate(d["archivedAt"]),
            purgeAfter = parseDate(d["purgeAfter"]),
            pendingSync = false,
        )

    private fun mapQuestion(
        id: String,
        d: Map<String, Any?>,
        testId: String,
        clubId: String,
    ): TestQuestionEntity =
        TestQuestionEntity(
            id = id,
            testId = (d["testId"] as? String) ?: testId,
            clubId = (d["clubId"] as? String) ?: clubId,
            honorId = d["honorId"] as? String ?: "",
            type = d["type"] as? String ?: "choice",
            text = d["text"] as? String ?: "",
            options = (d["options"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            correctAnswer = d["correctAnswer"] as? String ?: "",
            points = (d["points"] as? Number)?.toInt() ?: 1,
            order = (d["order"] as? Number)?.toInt() ?: 0,
        )

    private fun parseDate(v: Any?): Date? = when (v) {
        null -> null
        is Date -> v
        is Timestamp -> v.toDate()
        is Long -> Date(v)
        else -> null
    }
}
