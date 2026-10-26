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

    @Query("SELECT * FROM honor_progress WHERE userId = :userId AND requirementId = :reqId LIMIT 1")
    suspend fun getProgressForRequirement(userId: String, reqId: String): HonorProgressEntity?

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

    @Query("SELECT COUNT(*) FROM honor_progress WHERE userId = :userId AND status = 'approved'")
    suspend fun countApprovedRequirementsByUser(userId: String): Int

    /** Баг #6: одобрено требований за период по клубу (мультиклубность). */
    @Query("""
        SELECT COUNT(*) FROM honor_progress hp
        WHERE hp.status = 'approved'
          AND hp.approvedAt BETWEEN :from AND :to
          AND EXISTS (
              SELECT 1 FROM user_clubs uc
              WHERE uc.userId = hp.userId AND uc.clubId = :clubId
          )
    """)
    suspend fun countApprovedInPeriod(clubId: String, from: Long, to: Long): Int

    /** Баг #6: submitted требований за период по клубу (мультиклубность). */
    @Query("""
        SELECT COUNT(*) FROM honor_progress hp
        WHERE hp.status = 'submitted'
          AND hp.submittedAt BETWEEN :from AND :to
          AND EXISTS (
              SELECT 1 FROM user_clubs uc
              WHERE uc.userId = hp.userId AND uc.clubId = :clubId
          )
    """)
    suspend fun countPendingInPeriod(clubId: String, from: Long, to: Long): Int

    /** Завершено специализаций за период по клубу (мультиклубность). */
    @Query("""
        SELECT COUNT(*) FROM honor_completions hc
        WHERE hc.completedAt BETWEEN :from AND :to
          AND EXISTS (
              SELECT 1 FROM user_clubs uc
              WHERE uc.userId = hc.userId AND uc.clubId = :clubId
          )
    """)
    suspend fun countSpecializationsCompletedInPeriod(clubId: String, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM verse_progress WHERE userId = :userId")
    suspend fun countVersesByUser(userId: String): Int

    @Query("SELECT COUNT(*) FROM book_reports WHERE userId = :userId")
    suspend fun countBookReportsByUser(userId: String): Int

    // ---------- Прогресс специализаций: все тины клуба ----------

    @Query("SELECT * FROM honor_progress WHERE userId = :userId")
    fun observeAllProgressByUser(userId: String): Flow<List<HonorProgressEntity>>

    @Query("""
        SELECT hp.* FROM honor_progress hp
        JOIN users u ON hp.userId = u.id
        WHERE u.clubId = :clubId AND u.role = 'teen'
    """)
    fun observeProgressByClubTeens(clubId: String): Flow<List<HonorProgressEntity>>

    @Query("""
        SELECT hc.* FROM honor_completions hc
        JOIN users u ON hc.userId = u.id
        WHERE u.clubId = :clubId AND u.role = 'teen'
    """)
    fun observeCompletionsByClubTeens(clubId: String): Flow<List<HonorCompletionEntity>>

    /** Все требования всех специализаций — для ScoutDetail без suspend в combine. */
    @Query("SELECT * FROM honor_requirements ORDER BY honorId, `order`")
    fun observeAllRequirements(): Flow<List<HonorRequirementEntity>>

    // ✅ ДОБАВЛЕНО: Для экрана одобрения директора
    @Query("""SELECT hp.* FROM honor_progress hp
              JOIN users u ON hp.userId = u.id
              WHERE u.clubId = :clubId AND hp.status = 'submitted'
              ORDER BY hp.submittedAt DESC""")
    fun observePendingApprovalsByClub(clubId: String): Flow<List<HonorProgressEntity>>

    // ✅ ДОБАВЛЕНО: Для автоматического сброса версии контента
    @Query("DELETE FROM content_versions")
    suspend fun clearContentVersion()

    @Query("DELETE FROM honor_requirements")
    suspend fun clearAllRequirements()

    @Query("DELETE FROM honors")
    suspend fun clearAllHonors()
}
