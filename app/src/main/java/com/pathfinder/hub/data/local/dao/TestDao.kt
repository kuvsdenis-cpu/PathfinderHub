package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.learning.OpenAnswerReviewEntity
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import com.pathfinder.hub.data.local.entity.learning.TestEntity
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TestDao {

    // ---------- Тесты ----------

    @Query("SELECT * FROM tests WHERE id = :id")
    suspend fun getTest(id: String): TestEntity?

    @Query("""
        SELECT * FROM tests
        WHERE archivedAt IS NULL
          AND (clubId IS NULL OR clubId = :clubId)
        ORDER BY title
    """)
    fun observeAllForClub(clubId: String): Flow<List<TestEntity>>

    @Query("""
        SELECT * FROM tests
        WHERE archivedAt IS NULL
          AND scope = :scope
          AND (scopeId = :scopeId OR (:scopeId IS NULL AND scopeId IS NULL))
          AND (clubId IS NULL OR clubId = :clubId)
        ORDER BY title
    """)
    fun observeTestsForScope(clubId: String, scope: String, scopeId: String?): Flow<List<TestEntity>>

    @Query("""
        SELECT * FROM tests
        WHERE archivedAt IS NULL
          AND scope = :scope
          AND (scopeId = :scopeId OR (:scopeId IS NULL AND scopeId IS NULL))
          AND (clubId IS NULL OR clubId = :clubId)
        ORDER BY title
    """)
    suspend fun getTestsForScope(clubId: String, scope: String, scopeId: String?): List<TestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTest(t: TestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTests(ts: List<TestEntity>)

    @Query("DELETE FROM tests WHERE id = :id")
    suspend fun deleteTest(id: String)

    @Query("SELECT COUNT(*) FROM tests")
    suspend fun getTestsCount(): Int

    /** Все тесты клуба (включая архивные) — для tombstone-сравнения в pull. */
    @Query("SELECT * FROM tests WHERE clubId = :clubId")
    suspend fun getAllTestsForClub(clubId: String): List<TestEntity>

    // ---------- Вопросы ----------

    @Query("SELECT * FROM test_questions WHERE testId = :testId ORDER BY `order`")
    suspend fun getQuestionsForTest(testId: String): List<TestQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestions(qs: List<TestQuestionEntity>)

    @Query("DELETE FROM test_questions WHERE testId = :testId")
    suspend fun deleteQuestionsForTest(testId: String)

    @Query("DELETE FROM test_questions WHERE id = :id")
    suspend fun deleteQuestion(id: String)

    @Query("SELECT COUNT(*) FROM test_questions")
    suspend fun getQuestionsCount(): Int

    // ---------- Попытки ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttempt(a: TestAttemptEntity)

    @Query("SELECT MAX(attemptNumber) FROM test_attempts WHERE userId = :userId AND testId = :testId")
    suspend fun getLastAttemptNumber(userId: String, testId: String): Int?

    @Query("""
        SELECT * FROM test_attempts
        WHERE userId = :userId AND testId = :testId
        ORDER BY completedAt DESC LIMIT 1
    """)
    suspend fun getLatestAttempt(userId: String, testId: String): TestAttemptEntity?

    @Query("SELECT COUNT(*) FROM test_attempts WHERE userId = :userId AND passed = 1")
    suspend fun countPassedTestsByUser(userId: String): Int

    @Query("SELECT * FROM test_attempts WHERE userId = :userId ORDER BY completedAt DESC")
    suspend fun getAttemptsByUser(userId: String): List<TestAttemptEntity>

    /** Все попытки пользователя (включая pendingSync) — для tombstone в pull. */
    @Query("SELECT * FROM test_attempts WHERE userId = :userId")
    suspend fun getAllAttemptsByUser(userId: String): List<TestAttemptEntity>

    @Query("DELETE FROM test_attempts WHERE id = :id")
    suspend fun deleteAttempt(id: String)

    @Query("SELECT * FROM test_attempts WHERE userId = :userId ORDER BY completedAt DESC")
    fun observeAttemptsByUser(userId: String): Flow<List<TestAttemptEntity>>

    @Query("SELECT * FROM test_attempts WHERE id = :id")
    suspend fun getAttemptById(id: String): TestAttemptEntity?

    @Query("UPDATE test_attempts SET score = :score, passed = :passed WHERE id = :id")
    suspend fun updateAttemptScorePassed(id: String, score: Int, passed: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttempts(attempts: List<TestAttemptEntity>)

    /**
     * ID тестов, которые пользователь уже прошёл (passed = 1).
     * Используется секцией TestsForScopeSection для отображения галочки.
     */
    @Query("""
        SELECT DISTINCT testId FROM test_attempts
        WHERE userId = :userId AND passed = 1 AND testId IN (:testIds)
    """)
    suspend fun getPassedTestIds(userId: String, testIds: List<String>): List<String>

    // ---------- Открытые ответы ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOpenAnswer(r: OpenAnswerReviewEntity)

    @Query("SELECT * FROM open_answer_reviews WHERE attemptId = :attemptId")
    suspend fun getOpenAnswersForAttempt(attemptId: String): List<OpenAnswerReviewEntity>

    @Query("SELECT * FROM open_answer_reviews WHERE attemptId = :attemptId")
    fun observeOpenAnswersForAttempt(attemptId: String): Flow<List<OpenAnswerReviewEntity>>

    @Query("SELECT * FROM open_answer_reviews WHERE reviewedBy IS NULL")
    fun observePendingOpenAnswers(): Flow<List<OpenAnswerReviewEntity>>

    @Query("""
        SELECT COUNT(*) FROM open_answer_reviews
        WHERE attemptId = :attemptId AND reviewedBy IS NULL
    """)
    suspend fun countUnreviewedForAttempt(attemptId: String): Int

    /**
     * Попытки, в которых есть хотя бы один непроверенный long_text ответ.
     * Для экрана «Открытые ответы».
     */
    @Query("""
        SELECT * FROM test_attempts
        WHERE id IN (SELECT DISTINCT attemptId FROM open_answer_reviews WHERE reviewedBy IS NULL)
        ORDER BY completedAt DESC
    """)
    fun observeAttemptsWithPendingOpenAnswers(): Flow<List<TestAttemptEntity>>

    @Query("""
        SELECT COUNT(*) FROM test_attempts
        WHERE id IN (SELECT DISTINCT attemptId FROM open_answer_reviews WHERE reviewedBy IS NULL)
    """)
    fun countAttemptsWithPendingOpenAnswers(): Flow<Int>
}
