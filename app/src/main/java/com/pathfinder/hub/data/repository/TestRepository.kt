package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.TestDao
import com.pathfinder.hub.data.local.entity.learning.OpenAnswerReviewEntity
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import com.pathfinder.hub.data.local.entity.learning.TestEntity
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.remote.FirestoreTestAttemptDataSource
import com.pathfinder.hub.data.remote.FirestoreTestDataSource
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий универсальных тестов.
 *
 * Источник правды:
 *   - глобальные тесты (clubId = null) — из tests.json, сидятся SeedDatabaseWorker
 *   - локальные тесты — создаются в редакторе, clubId = <клуб>
 */
@Singleton
class TestRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: TestDao,
    private val syncQueueManager: SyncQueueManager,
    private val firestoreDataSource: FirestoreTestDataSource,
    private val firestoreTestAttemptDataSource: FirestoreTestAttemptDataSource,
) {

    // ---------- Чтение ----------

    suspend fun getTest(id: String): TestEntity? = dao.getTest(id)

    fun observeAllForClub(clubId: String): Flow<List<TestEntity>> =
        dao.observeAllForClub(clubId)

    fun observeTestsForScope(clubId: String, scope: String, scopeId: String?): Flow<List<TestEntity>> =
        dao.observeTestsForScope(clubId, scope, scopeId)

    suspend fun getTestsForScope(clubId: String, scope: String, scopeId: String?): List<TestEntity> =
        dao.getTestsForScope(clubId, scope, scopeId)

    suspend fun getQuestionsForTest(testId: String): List<TestQuestionEntity> =
        dao.getQuestionsForTest(testId)

    // ---------- Запись ----------

    suspend fun upsertTest(t: TestEntity) {
        db.withTransaction {
            dao.upsertTest(t)
            // Синкаем только клубные. Глобальные (clubId = null) — из tests.json, не синкаются.
            if (t.clubId != null) {
                syncQueueManager.enqueue("test", t.id, "UPDATE", t)
            }
        }
    }

    suspend fun deleteTest(id: String) {
        db.withTransaction {
            val existing = dao.getTest(id)
            dao.deleteTest(id)
            if (existing != null) {
                syncQueueManager.enqueue("test", id, "DELETE", existing)
            }
        }
    }

    /**
     * Перезапись вопросов теста. Используется в редакторе:
     * старые вопросы удаляются, новые вставляются.
     */
    suspend fun replaceQuestions(testId: String, questions: List<TestQuestionEntity>) {
        db.withTransaction {
            dao.deleteQuestionsForTest(testId)
            val test = dao.getTest(testId)
            val clubId = test?.clubId
            val prepared = questions.map { it.copy(clubId = clubId) }
            if (prepared.isNotEmpty()) {
                dao.upsertQuestions(prepared)
            }
            // Синкаем только клубные вопросы.
            if (clubId != null) {
                prepared.forEach {
                    syncQueueManager.enqueue("test_question", it.id, "UPDATE", it)
                }
            }
        }
    }

    /** Сидинг вопросов без sync queue — только для контента из assets. */
    suspend fun seedQuestions(questions: List<TestQuestionEntity>) {
        db.withTransaction {
            dao.upsertQuestions(questions)
        }
    }

    suspend fun upsertAttempt(a: TestAttemptEntity) {
        db.withTransaction {
            dao.upsertAttempt(a)
            syncQueueManager.enqueue("test_attempt", a.id, "CREATE", a)
        }
    }

    suspend fun getLastAttemptNumber(userId: String, testId: String): Int? =
        dao.getLastAttemptNumber(userId, testId)

    suspend fun getLatestAttempt(userId: String, testId: String): TestAttemptEntity? =
        dao.getLatestAttempt(userId, testId)

    suspend fun countPassedTestsByUser(userId: String): Int =
        dao.countPassedTestsByUser(userId)

    /** ID тестов, которые пользователь уже прошёл. */
    suspend fun getPassedTestIds(userId: String, testIds: List<String>): List<String> =
        dao.getPassedTestIds(userId, testIds)

    fun observeAttemptsByUser(userId: String): Flow<List<TestAttemptEntity>> =
        dao.observeAttemptsByUser(userId)

    suspend fun getAttemptById(id: String): TestAttemptEntity? = dao.getAttemptById(id)

    /**
     * Пересчёт score/passed после проверки long_text.
     * БЕЗ sync queue (пересчёт локальный).
     */
    suspend fun recalcAttemptAfterReview(attemptId: String) {
        val attempt = dao.getAttemptById(attemptId) ?: return
        val testId = attempt.testId ?: return
        val questions = dao.getQuestionsForTest(testId)
        if (questions.isEmpty()) return
        val test = dao.getTest(testId)
        val passScore = test?.passScore ?: 90

        val choiceMap = parseChoiceAnswers(attempt.choiceAnswers)
        val openAnswers = dao.getOpenAnswersForAttempt(attemptId)
            .associateBy { it.questionId }

        val totalPoints = questions.sumOf { it.points }.coerceAtLeast(1)
        val earned = questions.sumOf { q ->
            when (q.type) {
                "long_text" -> openAnswers[q.id]?.score ?: 0
                else -> {
                    val given = choiceMap[q.id]
                    if (given != null && given.equals(q.correctAnswer, ignoreCase = true))
                        q.points
                    else 0
                }
            }
        }
        val score = earned * 100 / totalPoints
        val passed = score >= passScore
        dao.updateAttemptScorePassed(attemptId, score, passed)
    }

    private fun parseChoiceAnswers(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            obj.keys().asSequence().associateWith { k -> obj.getString(k) }
        } catch (_: Exception) { emptyMap() }
    }

    suspend fun upsertOpenAnswer(r: OpenAnswerReviewEntity) {
        db.withTransaction {
            dao.upsertOpenAnswer(r)
            syncQueueManager.enqueue(
                "open_answer_review",
                "${r.attemptId}_${r.questionId}",
                "CREATE",
                r
            )
        }
    }

    suspend fun getOpenAnswersForAttempt(attemptId: String): List<OpenAnswerReviewEntity> =
        dao.getOpenAnswersForAttempt(attemptId)

    suspend fun countUnreviewedForAttempt(attemptId: String): Int =
        dao.countUnreviewedForAttempt(attemptId)

    fun observePendingOpenAnswers(): Flow<List<OpenAnswerReviewEntity>> =
        dao.observePendingOpenAnswers()

    fun observeAttemptsWithPendingOpenAnswers(): Flow<List<TestAttemptEntity>> =
        dao.observeAttemptsWithPendingOpenAnswers()

    fun countAttemptsWithPendingOpenAnswers(): Flow<Int> =
        dao.countAttemptsWithPendingOpenAnswers()

    // ---------- Pull (Firestore → Room, БЕЗ sync queue) ----------

    /**
     * Подписка на клубные тесты.
     * Обновляет Room, но НЕ ставит в sync queue (защита от зацикливания).
     * Должна быть запущена в coroutine, который отменяется при logout/выходе.
     */
    suspend fun pullClubTests(clubId: String) {
        if (clubId.isBlank()) return
        firestoreDataSource.observeClubTests(clubId).collect { snap ->
            try {
                // Защита: ошибка или пустой кэш — не трогаем локальные данные.
                if (snap.hasError) return@collect
                if (snap.fromCache && snap.items.isEmpty()) return@collect

                // 1. Upsert пришедшие.
                if (snap.items.isNotEmpty()) {
                    dao.upsertTests(snap.items)
                }

                // 2. Удалить локальные, которых нет на сервере.
                //    Только если snapshot НЕ из кэша (полный), и
                //    только те, что не ждут отправки (pendingSync = false).
                if (!snap.fromCache) {
                    val remoteIds = snap.items.map { it.id }.toSet()
                    val toDelete = dao.getAllTestsForClub(clubId)
                        .filter { it.id !in remoteIds && !it.pendingSync }
                    toDelete.forEach { dao.deleteTest(it.id) }
                }
            } catch (e: Exception) {
                // ignore — pull не должен падать на одну итерацию
            }
        }
    }

    /**
     * Подписка на вопросы теста.
     * Использует upsert (НЕ replace) — не затирает локальные правки редактора.
     */
    suspend fun pullTestQuestions(clubId: String, testId: String) {
        if (clubId.isBlank() || testId.isBlank()) return
        firestoreDataSource.observeTestQuestions(clubId, testId).collect { snap ->
            try {
                // Защита: ошибка или пустой кэш — не трогаем локальные данные.
                if (snap.hasError) return@collect
                if (snap.fromCache && snap.items.isEmpty()) return@collect

                // 1. Upsert пришедшие.
                if (snap.items.isNotEmpty()) {
                    dao.upsertQuestions(snap.items)
                }

                // 2. Удалить локальные, которых нет на сервере.
                //    Аналог tombstone для вопросов.
                if (!snap.fromCache) {
                    val remoteIds = snap.items.map { it.id }.toSet()
                    val local = dao.getQuestionsForTest(testId)
                    val toDelete = local.filter { it.id !in remoteIds }
                    // deleteQuestionsForTest удаляет ВСЕ вопросы теста — не подходит.
                    // Нужен точечный delete.
                    toDelete.forEach { dao.deleteQuestion(it.id) }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Подписка на попытки тестов пользователя.
     * БЕЗ sync queue — защита от зацикливания (pull не должен порождать push).
     */
    suspend fun pullTestAttempts(userId: String) {
        if (userId.isBlank()) return
        firestoreTestAttemptDataSource.observeTestAttempts(userId).collect { snap ->
            try {
                // Защита: ошибка или пустой кэш — не трогаем локальные данные.
                if (snap.hasError) return@collect
                if (snap.fromCache && snap.items.isEmpty()) return@collect

                // 1. Upsert пришедшие.
                if (snap.items.isNotEmpty()) {
                    dao.upsertAttempts(snap.items)
                }

                // 2. Удалить локальные, которых нет на сервере.
                //    Только если snapshot НЕ из кэша, и только те, что не ждут отправки.
                if (!snap.fromCache) {
                    val remoteIds = snap.items.map { it.id }.toSet()
                    val toDelete = dao.getAllAttemptsByUser(userId)
                        .filter { it.id !in remoteIds && !it.pendingSync }
                    toDelete.forEach { dao.deleteAttempt(it.id) }
                }
            } catch (e: Exception) {
                // ignore — pull не должен падать на одну итерацию
            }
        }
    }
}
