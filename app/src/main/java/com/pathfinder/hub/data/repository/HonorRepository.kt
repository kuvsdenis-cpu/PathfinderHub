package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
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
import com.pathfinder.hub.data.local.entity.learning.VerseProgressEntity
import com.pathfinder.hub.data.local.relation.HonorWithRequirements
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject

class HonorRepository @Inject constructor(
    private val db: AppDatabase,
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

    fun observeAllProgressByUser(userId: String): Flow<List<HonorProgressEntity>> =
        dao.observeAllProgressByUser(userId)

    fun observeProgressByClubTeens(clubId: String): Flow<List<HonorProgressEntity>> =
        dao.observeProgressByClubTeens(clubId)

    fun observeCompletionsByClubTeens(clubId: String): Flow<List<HonorCompletionEntity>> =
        dao.observeCompletionsByClubTeens(clubId)

    fun observeAllRequirements(): Flow<List<HonorRequirementEntity>> = dao.observeAllRequirements()
    suspend fun getRequirements(honorId: String): List<HonorRequirementEntity> = dao.getRequirements(honorId)
    fun observeProgress(userId: String, honorId: String): Flow<List<HonorProgressEntity>> = dao.observeProgress(userId, honorId)
    suspend fun getProgressSnapshot(userId: String, honorId: String): List<HonorProgressEntity> = dao.getProgressSnapshot(userId, honorId)
    fun observeCompletions(userId: String): Flow<List<HonorCompletionEntity>> = dao.observeCompletions(userId)
    fun observePendingDrafts(): Flow<List<HonorDraftEntity>> = dao.observePendingDrafts()
    suspend fun getLatestContentVersion(): ContentVersionEntity? = dao.getLatestContentVersion()
    fun observePendingApprovalsByClub(clubId: String): Flow<List<HonorProgressEntity>> =
        dao.observePendingApprovalsByClub(clubId)

    /** Баг #6: одобрено за период по клубу. */
    suspend fun countApprovedInPeriod(clubId: String, from: Long, to: Long): Int =
        dao.countApprovedInPeriod(clubId, from, to)

    /** Баг #6: submitted за период по клубу. */
    suspend fun countPendingInPeriod(clubId: String, from: Long, to: Long): Int =
        dao.countPendingInPeriod(clubId, from, to)

    /** Завершено специализаций за период по клубу. */
    suspend fun countSpecializationsCompletedInPeriod(clubId: String, from: Long, to: Long): Int =
        dao.countSpecializationsCompletedInPeriod(clubId, from, to)
    suspend fun clearContentVersion() = dao.clearContentVersion()

    // ---------- Сидовый контент (без enqueue) ----------
    suspend fun upsertCategory(c: HonorCategoryEntity) = dao.upsertCategory(c)
    suspend fun upsertCategories(cs: List<HonorCategoryEntity>) = dao.upsertCategories(cs)
    suspend fun upsertHonors(hs: List<HonorEntity>) = dao.upsertHonors(hs)
    suspend fun clearAllHonors() = dao.clearAllHonors()
    suspend fun clearAllHonorRequirements() = dao.clearAllRequirements()
    suspend fun upsertRequirements(rs: List<HonorRequirementEntity>) = dao.upsertRequirements(rs)
    suspend fun upsertBooks(bs: List<BookEntity>) = dao.upsertBooks(bs)
    suspend fun upsertVerses(vs: List<MemoryVerseEntity>) = dao.upsertVerses(vs)
    suspend fun upsertContentVersion(v: ContentVersionEntity) = dao.upsertContentVersion(v)
    suspend fun upsertVersion(v: HonorVersionEntity) = dao.upsertVersion(v)

    // ---------- Пользовательские данные (с транзакциями + enqueue) ----------
    suspend fun upsertProgress(p: HonorProgressEntity) {
        db.withTransaction {
            dao.upsertProgress(p)
            syncQueueManager.enqueue(
                entityType = "honor_progress",
                entityId = "${p.userId}_${p.requirementId}",
                operation = "UPDATE",
                payload = p
            )
        }
    }

    suspend fun attachReportToRequirement(
        userId: String,
        requirementId: String,
        fileUrl: String,
        fileName: String
    ) {
        db.withTransaction {
            val existing = dao.getProgressForRequirement(userId, requirementId)
            val honorId = existing?.honorId ?: ""

            val newProgress = HonorProgressEntity(
                userId = userId,
                honorId = honorId,
                requirementId = requirementId,
                status = "submitted",
                submittedAt = Date(),
                approvedBy = existing?.approvedBy,
                approvedAt = existing?.approvedAt,
                mediaUrls = (existing?.mediaUrls ?: emptyList()) + fileUrl,
                comment = "Файл: $fileName",
                pendingSync = true
            )

            dao.upsertProgress(newProgress)
            syncQueueManager.enqueue(
                entityType = "honor_progress",
                entityId = "${userId}_${requirementId}",
                operation = "UPDATE",
                payload = newProgress
            )
        }
    }

    suspend fun approveRequirement(
        userId: String,
        requirementId: String,
        status: String,
        approvedBy: String,
        at: Long
    ) {
        db.withTransaction {
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
    }

    suspend fun upsertCompletion(c: HonorCompletionEntity) {
        db.withTransaction {
            dao.upsertCompletion(c)
            syncQueueManager.enqueue(
                entityType = "honor_completion",
                entityId = "${c.userId}_${c.honorId}",
                operation = "CREATE",
                payload = c
            )
        }
    }

    suspend fun upsertDraft(d: HonorDraftEntity) {
        db.withTransaction {
            dao.upsertDraft(d)
            syncQueueManager.enqueue(
                entityType = "honor_draft",
                entityId = d.id,
                operation = "CREATE",
                payload = d
            )
        }
    }

    suspend fun upsertBookReport(r: BookReportEntity) {
        db.withTransaction {
            dao.upsertBookReport(r)
            syncQueueManager.enqueue(
                entityType = "book_report",
                entityId = r.id,
                operation = "CREATE",
                payload = r
            )
        }
    }

    suspend fun upsertVerseProgress(p: VerseProgressEntity) {
        db.withTransaction {
            dao.upsertVerseProgress(p)
            syncQueueManager.enqueue(
                entityType = "verse_progress",
                entityId = "${p.userId}_${p.verseId}",
                operation = "UPDATE",
                payload = p
            )
        }
    }
}