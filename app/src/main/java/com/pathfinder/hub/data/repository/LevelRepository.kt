package com.pathfinder.hub.data.repository

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
    private val syncQueueManager: SyncQueueManager
) {
    // ---------- Чтение ----------
    fun observeLevels(): Flow<List<LevelEntity>> = dao.observeLevels()
    fun observeLevelsWithSections(): Flow<List<LevelWithSections>> = dao.observeLevelsWithSections()
    suspend fun getLevel(id: String): LevelEntity? = dao.getLevel(id)

    fun observeSections(levelId: String): Flow<List<LevelSectionEntity>> = dao.observeSections(levelId)
    suspend fun getSections(levelId: String): List<LevelSectionEntity> = dao.getSections(levelId)

    fun observeRequirements(levelId: String): Flow<List<LevelRequirementEntity>> = dao.observeRequirements(levelId)
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

    fun observePendingApprovalsByClub(clubId: String): Flow<List<LevelProgressEntity>> =
        dao.observePendingApprovalsByClub(clubId)

    // ---------- Сидовый контент ----------
    suspend fun upsertLevels(levels: List<LevelEntity>) = dao.upsertLevels(levels)
    suspend fun upsertLevel(level: LevelEntity) = dao.upsertLevel(level)
    suspend fun upsertSections(sections: List<LevelSectionEntity>) = dao.upsertSections(sections)
    suspend fun upsertRequirements(reqs: List<LevelRequirementEntity>) = dao.upsertRequirements(reqs)

    // ---------- Очистка перед перезагрузкой ----------
    suspend fun clearAllRequirements() = dao.clearAllRequirements()
    suspend fun clearAllSections() = dao.clearAllSections()

    // ---------- Пользовательские данные (с enqueue) ----------
    suspend fun upsertProgress(p: LevelProgressEntity) {
        dao.upsertProgress(p)
        syncQueueManager.enqueue(
            entityType = "level_progress",
            entityId = "${p.userId}_${p.requirementId}",
            operation = "UPDATE",
            payload = p
        )
    }

    /**
     * Прикрепить загруженный файл (отчёт) к требованию ступени.
     * Если записи прогресса ещё нет — создаёт новую.
     *
     * ВАЖНО: levelId берём из уже существующей записи прогресса.
     * Если её нет — levelId останется пустым, и запись всё равно сохранится,
     * но лучше, чтобы LevelProgressEntity уже существовала (создаётся при первом действии).
     */
    suspend fun attachReportToRequirement(
        userId: String,
        requirementId: String,
        fileUrl: String,
        fileName: String
    ) {
        val existing = dao.getProgressForRequirement(userId, requirementId)
        val levelId = existing?.levelId ?: ""

        val updated = LevelProgressEntity(
            userId = userId,
            levelId = levelId,
            requirementId = requirementId,
            status = "submitted",
            submittedAt = Date(),
            approvedBy = existing?.approvedBy,
            approvedAt = existing?.approvedAt,
            comment = "Файл: $fileName ($fileUrl)",
            pendingSync = true
        )

        upsertProgress(updated)
    }

    suspend fun approveRequirement(userId: String, requirementId: String, status: String, approvedBy: String, at: Long) {
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

    suspend fun clearProgressForRequirement(userId: String, requirementId: String) {
        dao.clearProgressForRequirement(userId, requirementId)
        syncQueueManager.enqueue(
            entityType = "level_progress",
            entityId = "${userId}_${requirementId}",
            operation = "DELETE",
            payload = mapOf("userId" to userId, "requirementId" to requirementId)
        )
    }

    suspend fun upsertCompletion(c: LevelCompletionEntity) {
        dao.upsertCompletion(c)
        syncQueueManager.enqueue(
            entityType = "level_completion",
            entityId = "${c.userId}_${c.levelId}",
            operation = "CREATE",
            payload = c
        )
    }

    suspend fun upsertChecklist(c: LeaderChecklistEntity) {
        dao.upsertChecklist(c)
        syncQueueManager.enqueue(
            entityType = "leader_checklist",
            entityId = "${c.leaderId}_${c.childId}_${c.levelId}",
            operation = "UPDATE",
            payload = c
        )
    }

    // ==================== DEBUG ====================
    suspend fun debugDistinctSectionLevelIds(): List<String> = dao.debugDistinctSectionLevelIds()
    suspend fun debugDistinctRequirementLevelIds(): List<String> = dao.debugDistinctRequirementLevelIds()
    suspend fun debugDumpSections(): List<LevelSectionEntity> = dao.debugDumpSections()
    suspend fun debugDumpRequirements(): List<LevelRequirementEntity> = dao.debugDumpRequirements()
}