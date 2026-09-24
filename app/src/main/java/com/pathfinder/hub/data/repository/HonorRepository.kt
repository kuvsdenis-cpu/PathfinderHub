package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.HonorDao
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.local.entity.learning.BookReportEntity
import com.pathfinder.hub.data.local.entity.learning.ContentVersionEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCategoryEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.HonorDraftEntity
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.local.entity.learning.HonorRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.HonorVersionEntity
import com.pathfinder.hub.data.local.entity.learning.MemoryVerseEntity
import com.pathfinder.hub.data.local.entity.learning.OpenAnswerReviewEntity
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.local.entity.learning.VerseProgressEntity
import com.pathfinder.hub.data.local.relation.HonorWithRequirements
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class HonorRepository @Inject constructor(
    private val dao: HonorDao,
    private val syncQueueManager: SyncQueueManager
) {
    // ---------- Чтение (без enqueue) ----------
    fun observeCategories(): Flow<List<HonorCategoryEntity>> = dao.observeCategories()
    fun observeAllHonors(): Flow<List<HonorEntity>> = dao.observeAllHonors()
    fun observeHonorsByCategory(categoryId: String): Flow<List<HonorEntity>> = dao.observeHonorsByCategory(categoryId)
    suspend fun getHonor(id: String): HonorEntity? = dao.getHonor(id)
    fun observeHonorWithRequirements(id: String): Flow<HonorWithRequirements?> = dao.observeHonorWithRequirements(id)
    fun searchHonors(query: String): Flow<List<HonorEntity>> = dao.searchHonors(query)
    fun observeRequirements(honorId: String): Flow<List<HonorRequirementEntity>> = dao.observeRequirements(honorId)
    suspend fun getRequirements(honorId: String): List<HonorRequirementEntity> = dao.getRequirements(honorId)
    fun observeProgress(userId: String, honorId: String): Flow<List<HonorProgressEntity>> = dao.observeProgress(userId, honorId)
    suspend fun getProgressSnapshot(userId: String, honorId: String): List<HonorProgressEntity> = dao.getProgressSnapshot(userId, honorId)
    fun observeCompletions(userId: String): Flow<List<HonorCompletionEntity>> = dao.observeCompletions(userId)
    suspend fun getRandomQuestions(honorId: String, limit: Int): List<TestQuestionEntity> = dao.getRandomQuestions(honorId, limit)
    suspend fun getLastAttemptNumber(userId: String, honorId: String): Int? = dao.getLastAttemptNumber(userId, honorId)
    fun observePendingDrafts(): Flow<List<HonorDraftEntity>> = dao.observePendingDrafts()
    suspend fun getLatestContentVersion(): ContentVersionEntity? = dao.getLatestContentVersion()

    // ---------- Сидовый контент (без enqueue) ----------
    suspend fun upsertCategory(c: HonorCategoryEntity) = dao.upsertCategory(c)
    suspend fun upsertCategories(cs: List<HonorCategoryEntity>) = dao.upsertCategories(cs)
    suspend fun upsertHonors(hs: List<HonorEntity>) = dao.upsertHonors(hs)
    suspend fun upsertRequirements(rs: List<HonorRequirementEntity>) = dao.upsertRequirements(rs)
    suspend fun upsertQuestions(qs: List<TestQuestionEntity>) = dao.upsertQuestions(qs)
    suspend fun upsertBooks(bs: List<BookEntity>) = dao.upsertBooks(bs)
    suspend fun upsertVerses(vs: List<MemoryVerseEntity>) = dao.upsertVerses(vs)
    suspend fun upsertContentVersion(v: ContentVersionEntity) = dao.upsertContentVersion(v)
    suspend fun upsertVersion(v: HonorVersionEntity) = dao.upsertVersion(v)

    // ---------- Пользовательские данные (с enqueue) ----------

    suspend fun upsertProgress(p: HonorProgressEntity) {
        dao.upsertProgress(p)
        syncQueueManager.enqueue(
            entityType = "honor_progress",
            entityId = "${p.userId}_${p.requirementId}",
            operation = "UPDATE",
            payload = p
        )
    }

    suspend fun approveRequirement(userId: String, requirementId: String, status: String, approvedBy: String, at: Long) {
        dao.approveRequirement(userId, requirementId, status, approvedBy, at)
        syncQueueManager.enqueue(
            entityType = "honor_progress",
            entityId = "${userId}_${requirementId}",
            operation = "UPDATE",
            payload = mapOf(
                "userId" to userId,
                "requirementId" to requirementId,
                "status" to status,
                "approvedBy" to approvedBy,
                "approvedAt" to at
            )
        )
    }

    suspend fun upsertCompletion(c: HonorCompletionEntity) {
        dao.upsertCompletion(c)
        syncQueueManager.enqueue(
            entityType = "honor_completion",
            entityId = "${c.userId}_${c.honorId}",
            operation = "CREATE",
            payload = c
        )
    }

    suspend fun upsertAttempt(a: TestAttemptEntity) {
        dao.upsertAttempt(a)
        syncQueueManager.enqueue(
            entityType = "test_attempt",
            entityId = a.id,
            operation = "CREATE",
            payload = a
        )
    }

    suspend fun upsertOpenAnswer(r: OpenAnswerReviewEntity) {
        dao.upsertOpenAnswer(r)
        syncQueueManager.enqueue(
            entityType = "open_answer_review",
            entityId = "${r.attemptId}_${r.questionId}",
            operation = "CREATE",
            payload = r
        )
    }

    suspend fun upsertDraft(d: HonorDraftEntity) {
        dao.upsertDraft(d)
        syncQueueManager.enqueue(
            entityType = "honor_draft",
            entityId = d.id,
            operation = "CREATE",
            payload = d
        )
    }

    suspend fun upsertBookReport(r: BookReportEntity) {
        dao.upsertBookReport(r)
        syncQueueManager.enqueue(
            entityType = "book_report",
            entityId = r.id,
            operation = "CREATE",
            payload = r
        )
    }

    suspend fun upsertVerseProgress(p: VerseProgressEntity) {
        dao.upsertVerseProgress(p)
        syncQueueManager.enqueue(
            entityType = "verse_progress",
            entityId = "${p.userId}_${p.verseId}",
            operation = "UPDATE",
            payload = p
        )
    }
}