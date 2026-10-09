package com.pathfinder.hub.data.repository

import android.util.Log
import com.pathfinder.hub.data.local.dao.RequirementMaterialDao
import com.pathfinder.hub.data.local.dao.RequirementTaskDao
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import com.pathfinder.hub.data.remote.FirestoreRequirementContentDataSource
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий материалов и заданий требования.
 *
 * Чтение для UI:
 *   Room-first (мгновенно) + Firestore listener (актуализация).
 *
 * Запись:
 *   Только директор. Изменения пишутся в Room + Firestore,
 *   при провале Firestore — в SyncQueue.
 */
@Singleton
class RequirementContentRepository @Inject constructor(
    private val materialDao: RequirementMaterialDao,
    private val taskDao: RequirementTaskDao,
    private val firestore: FirestoreRequirementContentDataSource,
    private val syncQueue: SyncQueueManager,
) {

    companion object {
        private const val TAG = "ReqContentRepo"
        private const val ET_MATERIAL = "requirement_material"
        private const val ET_TASK = "requirement_task"
    }

    // ==================== ЧТЕНИЕ ====================

    fun observeMaterials(clubId: String, requirementId: String): Flow<List<RequirementMaterialEntity>> =
        firestore.observeMaterials(clubId, requirementId)
            .onEach { list ->
                if (list.isNotEmpty()) {
                    try { materialDao.upsertAll(list.map { it.copy(pendingSync = false) }) }
                    catch (e: Exception) { Log.w(TAG, "materialDao.upsertAll failed", e) }
                }
            }

    fun observeTasks(clubId: String, requirementId: String): Flow<List<RequirementTaskEntity>> =
        firestore.observeTasks(clubId, requirementId)
            .onEach { list ->
                if (list.isNotEmpty()) {
                    try { taskDao.upsertAll(list.map { it.copy(pendingSync = false) }) }
                    catch (e: Exception) { Log.w(TAG, "taskDao.upsertAll failed", e) }
                }
            }

    suspend fun getMaterialsFromRoom(clubId: String, requirementId: String): List<RequirementMaterialEntity> =
        materialDao.observeClubOverrides(requirementId, clubId).let { flow ->
            // однократно: getByRequirement + фильтр по clubId
            materialDao.getByRequirement(requirementId).filter { it.clubId == clubId }
        }

    suspend fun getTasksFromRoom(clubId: String, requirementId: String): List<RequirementTaskEntity> =
        taskDao.getByRequirement(requirementId).filter { it.clubId == clubId }

    // ==================== CRUD: MATERIALS ====================

    suspend fun createMaterial(
        clubId: String, requirementId: String, authorId: String,
        type: String, title: String, content: String, orderIndex: Int,
    ): Result<Unit> {
        val now = Date()
        val material = RequirementMaterialEntity(
            id = UUID.randomUUID().toString(),
            requirementId = requirementId,
            clubId = clubId,
            type = type, title = title, content = content,
            orderIndex = orderIndex,
            createdBy = authorId,
            createdAt = now, updatedAt = now,
        )
        return saveMaterial(material)
    }

    suspend fun saveMaterial(material: RequirementMaterialEntity): Result<Unit> {
        val updated = material.copy(updatedAt = Date())
        materialDao.upsert(updated.copy(pendingSync = true))
        val remote = firestore.upsertMaterial(updated)
        if (remote.isSuccess) {
            materialDao.clearPendingSync(updated.id)
        } else {
            syncQueue.enqueue(ET_MATERIAL, updated.id, "upsert", updated)
            Log.w(TAG, "material saved locally, queued for sync")
        }
        return remote
    }

    suspend fun deleteMaterial(clubId: String, requirementId: String, materialId: String): Result<Unit> {
        materialDao.deleteById(materialId)
        val remote = firestore.deleteMaterial(clubId, requirementId, materialId)
        if (remote.isFailure) {
            syncQueue.enqueue(ET_MATERIAL, materialId, "delete",
                mapOf("clubId" to clubId, "requirementId" to requirementId, "id" to materialId))
        }
        return remote
    }

    // ==================== CRUD: TASKS ====================

    suspend fun createTask(
        clubId: String, requirementId: String, authorId: String,
        type: String, title: String, description: String?,
        options: List<String>, correctAnswer: String?,
        isRequired: Boolean, orderIndex: Int,
    ): Result<Unit> {
        val now = Date()
        val task = RequirementTaskEntity(
            id = UUID.randomUUID().toString(),
            requirementId = requirementId, clubId = clubId,
            type = type, title = title, description = description,
            options = options, correctAnswer = correctAnswer,
            autoCheck = RequirementTaskEntity.isAutoCheckType(type),
            isRequired = isRequired, orderIndex = orderIndex,
            createdBy = authorId,
            createdAt = now, updatedAt = now,
        )
        return saveTask(task)
    }

    suspend fun saveTask(task: RequirementTaskEntity): Result<Unit> {
        val updated = task.copy(
            updatedAt = Date(),
            autoCheck = RequirementTaskEntity.isAutoCheckType(task.type)
        )
        taskDao.upsert(updated.copy(pendingSync = true))
        val remote = firestore.upsertTask(updated)
        if (remote.isSuccess) {
            taskDao.clearPendingSync(updated.id)
        } else {
            syncQueue.enqueue(ET_TASK, updated.id, "upsert", updated)
            Log.w(TAG, "task saved locally, queued for sync")
        }
        return remote
    }

    suspend fun deleteTask(clubId: String, requirementId: String, taskId: String): Result<Unit> {
        taskDao.deleteById(taskId)
        val remote = firestore.deleteTask(clubId, requirementId, taskId)
        if (remote.isFailure) {
            syncQueue.enqueue(ET_TASK, taskId, "delete",
                mapOf("clubId" to clubId, "requirementId" to requirementId, "id" to taskId))
        }
        return remote
    }

    // ==================== FLAG / UNFLAG (наставник) ====================

    suspend fun flagMaterial(clubId: String, requirementId: String, materialId: String, by: String, comment: String?): Result<Unit> {
        val now = System.currentTimeMillis()
        materialDao.flag(materialId, by, now, comment)
        return firestore.flagMaterial(clubId, requirementId, materialId, by, comment)
    }

    suspend fun unflagMaterial(clubId: String, requirementId: String, materialId: String): Result<Unit> {
        materialDao.unflag(materialId, System.currentTimeMillis())
        return firestore.unflagMaterial(clubId, requirementId, materialId)
    }

    suspend fun flagTask(clubId: String, requirementId: String, taskId: String, by: String, comment: String?): Result<Unit> {
        val now = System.currentTimeMillis()
        taskDao.flag(taskId, by, now, comment)
        return firestore.flagTask(clubId, requirementId, taskId, by, comment)
    }

    suspend fun unflagTask(clubId: String, requirementId: String, taskId: String): Result<Unit> {
        taskDao.unflag(taskId, System.currentTimeMillis())
        return firestore.unflagTask(clubId, requirementId, taskId)
    }
}
