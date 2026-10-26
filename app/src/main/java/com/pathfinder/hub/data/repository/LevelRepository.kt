package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.LevelDao
import com.pathfinder.hub.data.local.entity.learning.LeaderChecklistEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity
import com.pathfinder.hub.data.local.relation.LevelWithSections
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject

class LevelRepository @Inject constructor(
    private val dao: LevelDao,
    private val syncQueueManager: SyncQueueManager,
    private val database: AppDatabase
) {
    // ---------- Чтение ----------
    fun observeLevels(): Flow<List<LevelEntity>> = dao.observeLevels()
    fun observeLevelsWithSections(): Flow<List<LevelWithSections>> = dao.observeLevelsWithSections()
    suspend fun getLevel(id: String): LevelEntity? = dao.getLevel(id)

    fun observeSections(levelId: String): Flow<List<LevelSectionEntity>> = dao.observeSections(levelId)
    suspend fun getSections(levelId: String): List<LevelSectionEntity> = dao.getSections(levelId)

    fun observeRequirements(levelId: String): Flow<List<LevelRequirementEntity>> = dao.observeRequirements(levelId)

    fun observeAllProgressByUser(userId: String): Flow<List<LevelProgressEntity>> =
        dao.observeAllProgressByUser(userId)

    fun observeProgressByClubTeens(clubId: String): Flow<List<LevelProgressEntity>> =
        dao.observeProgressByClubTeens(clubId)

    fun observeCompletionsByClubTeens(clubId: String): Flow<List<LevelCompletionEntity>> =
        dao.observeCompletionsByClubTeens(clubId)

    fun observeAllRequirements(): Flow<List<LevelRequirementEntity>> = dao.observeAllRequirements()
    suspend fun getRequirements(levelId: String): List<LevelRequirementEntity> = dao.getRequirements(levelId)

    suspend fun getRequirementsBySection(levelId: String, sectionId: String): List<LevelRequirementEntity> =
        dao.getRequirementsBySection(levelId, sectionId)

    suspend fun getRequirementById(levelId: String, requirementId: String): LevelRequirementEntity? =
        dao.getRequirements(levelId).firstOrNull { it.id == requirementId }

    fun observeProgress(userId: String, levelId: String): Flow<List<LevelProgressEntity>> =
        dao.observeProgress(userId, levelId)

    suspend fun getProgressSnapshot(userId: String, levelId: String): List<LevelProgressEntity> =
        dao.getProgressSnapshot(userId, levelId)

    fun observeCompletions(userId: String): Flow<List<LevelCompletionEntity>> = dao.observeCompletions(userId)
    fun observeChecklist(leaderId: String, childId: String): Flow<LeaderChecklistEntity?> =
        dao.observeChecklist(leaderId, childId)

    fun observeMyChecklists(leaderId: String): Flow<List<LeaderChecklistEntity>> =
        dao.observeMyChecklists(leaderId)

    fun observePendingApprovalsByClub(clubId: String): Flow<List<LevelProgressEntity>> =
        dao.observePendingApprovalsByClub(clubId)

    /** Баг #6: одобрено за период по клубу. */
    suspend fun countApprovedInPeriod(clubId: String, from: Long, to: Long): Int =
        dao.countApprovedInPeriod(clubId, from, to)

    /** Баг #6: submitted за период по клубу. */
    suspend fun countPendingInPeriod(clubId: String, from: Long, to: Long): Int =
        dao.countPendingInPeriod(clubId, from, to)

    /** Завершено ступеней за период по клубу. */
    suspend fun countLevelsCompletedInPeriod(clubId: String, from: Long, to: Long): Int =
        dao.countLevelsCompletedInPeriod(clubId, from, to)

    // ---------- Сидовый контент ----------
    suspend fun upsertLevels(levels: List<LevelEntity>) = dao.upsertLevels(levels)
    suspend fun upsertLevel(level: LevelEntity) = dao.upsertLevel(level)
    suspend fun upsertSections(sections: List<LevelSectionEntity>) = dao.upsertSections(sections)
    suspend fun upsertRequirements(reqs: List<LevelRequirementEntity>) = dao.upsertRequirements(reqs)

    /**
     * Атомарная замена сидового контента уровней.
     * Всё внутри — одна транзакция: при исключении БД откатится
     * к состоянию до очистки, данные не потеряются.
     */
    suspend fun seedLevelsAtomically(block: suspend () -> Unit) {
        database.withTransaction { block() }
    }

    // ---------- Очистка перед перезагрузкой ----------
    suspend fun clearAllRequirements() = dao.clearAllRequirements()
    suspend fun clearAllSections() = dao.clearAllSections()
    suspend fun clearAllLevels() = dao.clearAllLevels()

    // ---------- Пользовательские данные (с enqueue + транзакция) ----------

    /**
     * Запись прогресса + постановка в очередь синхронизации — атомарно.
     * Если процесс умрёт между этими операциями, обе откатятся.
     */
    suspend fun upsertProgress(p: LevelProgressEntity) {
        database.withTransaction {
            dao.upsertProgress(p)
            syncQueueManager.enqueue(
                entityType = "level_progress",
                entityId = "${p.userId}_${p.requirementId}",
                operation = "UPDATE",
                payload = p
            )
        }
    }

    /**
     * Прикрепить загруженный файл (отчёт) к требованию ступени.
     * Если записи прогресса ещё нет — создаёт новую.
     * URL добавляется к mediaUrls (не перезаписывает).
     *
     * Не оборачиваем в отдельную транзакцию — внутри upsertProgress уже есть.
     * Но чтение existing и финальная запись — в одной транзакции, чтобы
     * параллельный вызов не затирал mediaUrls.
     */
    suspend fun attachReportToRequirement(
        userId: String,
        requirementId: String,
        fileUrl: String,
        fileName: String
    ) {
        database.withTransaction {
            val existing = dao.getProgressForRequirement(userId, requirementId)
            val levelId = existing?.levelId ?: ""
            val newMediaUrls = ((existing?.mediaUrls ?: emptyList()) + fileUrl).distinct()

            val updated = LevelProgressEntity(
                userId = userId,
                levelId = levelId,
                requirementId = requirementId,
                status = "submitted",
                submittedAt = Date(),
                approvedBy = existing?.approvedBy,
                approvedAt = existing?.approvedAt,
                comment = "Файл: $fileName",
                mediaUrls = newMediaUrls,
                pendingSync = true
            )

            dao.upsertProgress(updated)
            syncQueueManager.enqueue(
                entityType = "level_progress",
                entityId = "${userId}_${requirementId}",
                operation = "UPDATE",
                payload = updated
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
        database.withTransaction {
            dao.approveRequirement(userId, requirementId, status, approvedBy, at)
            syncQueueManager.enqueue(
                entityType = "level_progress",
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

    suspend fun clearProgressForRequirement(userId: String, requirementId: String) {
        database.withTransaction {
            dao.clearProgressForRequirement(userId, requirementId)
            syncQueueManager.enqueue(
                entityType = "level_progress",
                entityId = "${userId}_${requirementId}",
                operation = "DELETE",
                payload = mapOf("userId" to userId, "requirementId" to requirementId)
            )
        }
    }

    suspend fun upsertCompletion(c: LevelCompletionEntity) {
        database.withTransaction {
            dao.upsertCompletion(c)
            syncQueueManager.enqueue(
                entityType = "level_completion",
                entityId = "${c.userId}_${c.levelId}",
                operation = "CREATE",
                payload = c
            )
        }
    }

    suspend fun upsertChecklist(c: LeaderChecklistEntity) {
        database.withTransaction {
            dao.upsertChecklist(c)
            syncQueueManager.enqueue(
                entityType = "leader_checklist",
                entityId = "${c.leaderId}_${c.childId}_${c.levelId}",
                operation = "UPDATE",
                payload = c
            )
        }
    }

    // ==================== DEBUG ====================
    suspend fun debugDistinctSectionLevelIds(): List<String> = dao.debugDistinctSectionLevelIds()
    suspend fun debugDistinctRequirementLevelIds(): List<String> = dao.debugDistinctRequirementLevelIds()
    suspend fun debugDumpSections(): List<LevelSectionEntity> = dao.debugDumpSections()
    suspend fun debugDumpRequirements(): List<LevelRequirementEntity> = dao.debugDumpRequirements()
}