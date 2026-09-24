package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
import kotlinx.coroutines.flow.Flow

@Dao
interface HonorDao {

    @Query("SELECT * FROM honor_categories ORDER BY `order`")
    fun observeCategories(): Flow<List<HonorCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(c: HonorCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(cs: List<HonorCategoryEntity>)

    @Query("SELECT * FROM honors ORDER BY name")
    fun observeAllHonors(): Flow<List<HonorEntity>>

    @Query("SELECT * FROM honors WHERE categoryId = :categoryId ORDER BY name")
    fun observeHonorsByCategory(categoryId: String): Flow<List<HonorEntity>>

    @Query("SELECT * FROM honors WHERE id = :id")
    suspend fun getHonor(id: String): HonorEntity?

    @Transaction
    @Query("SELECT * FROM honors WHERE id = :id")
    fun observeHonorWithRequirements(id: String): Flow<HonorWithRequirements?>

    @Query("SELECT * FROM honors WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    fun searchHonors(query: String): Flow<List<HonorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHonors(hs: List<HonorEntity>)

    @Query("SELECT * FROM honor_requirements WHERE honorId = :honorId ORDER BY `order`")
    fun observeRequirements(honorId: String): Flow<List<HonorRequirementEntity>>

    @Query("SELECT * FROM honor_requirements WHERE honorId = :honorId ORDER BY `order`")
    suspend fun getRequirements(honorId: String): List<HonorRequirementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequirements(rs: List<HonorRequirementEntity>)

    @Query("SELECT * FROM honor_progress WHERE userId = :userId AND honorId = :honorId")
    fun observeProgress(userId: String, honorId: String): Flow<List<HonorProgressEntity>>

    @Query("SELECT * FROM honor_progress WHERE userId = :userId AND honorId = :honorId")
    suspend fun getProgressSnapshot(userId: String, honorId: String): List<HonorProgressEntity>

    @Query("SELECT * FROM honor_progress WHERE userId = :userId AND status = 'approved'")
    suspend fun getApprovedProgress(userId: String): List<HonorProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: HonorProgressEntity)

    @Query("UPDATE honor_progress SET status = :status, approvedBy = :by, approvedAt = :at WHERE userId = :userId AND requirementId = :reqId")
    suspend fun approveRequirement(userId: String, reqId: String, status: String, by: String, at: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(c: HonorCompletionEntity)

    @Query("SELECT * FROM honor_completions WHERE userId = :userId")
    fun observeCompletions(userId: String): Flow<List<HonorCompletionEntity>>

    @Query("SELECT * FROM honor_completions WHERE userId = :userId")
    suspend fun getCompletions(userId: String): List<HonorCompletionEntity>

    @Query("SELECT * FROM test_questions WHERE honorId = :honorId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestions(honorId: String, limit: Int): List<TestQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestions(qs: List<TestQuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttempt(a: TestAttemptEntity)

    @Query("SELECT MAX(attemptNumber) FROM test_attempts WHERE userId = :userId AND honorId = :honorId")
    suspend fun getLastAttemptNumber(userId: String, honorId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOpenAnswer(r: OpenAnswerReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersion(v: HonorVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(d: HonorDraftEntity)

    @Query("SELECT * FROM honor_drafts WHERE status = 'pending'")
    fun observePendingDrafts(): Flow<List<HonorDraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooks(bs: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookReport(r: BookReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVerses(vs: List<MemoryVerseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVerseProgress(p: VerseProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContentVersion(v: ContentVersionEntity)

    @Query("SELECT * FROM content_versions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestContentVersion(): ContentVersionEntity?
}
