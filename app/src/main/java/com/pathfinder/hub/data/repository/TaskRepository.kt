package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.TaskDao
import com.pathfinder.hub.data.local.entity.planning.AssignmentEntity
import com.pathfinder.hub.data.local.entity.planning.ConfirmationEntity
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TaskRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: TaskDao,
    private val syncQueueManager: SyncQueueManager
) {
    fun observeTask(id: String): Flow<TaskEntity?> = dao.observeTask(id)
    suspend fun getTask(id: String): TaskEntity? = dao.getTask(id)
    fun observeClubTasks(clubId: String): Flow<List<TaskEntity>> = dao.observeClubTasks(clubId)
    fun observeAssignedTasks(userId: String): Flow<List<TaskEntity>> = dao.observeAssignedTasks(userId)

    // ---------- Архивация ----------
    fun observeActiveAssignedTasks(userId: String): Flow<List<TaskEntity>> =
        dao.observeActiveAssignedTasks(userId)

    fun observeArchivedAssignedTasks(userId: String): Flow<List<TaskEntity>> =
        dao.observeArchivedAssignedTasks(userId)

    fun observeActiveClubTasks(clubId: String): Flow<List<TaskEntity>> =
        dao.observeActiveClubTasks(clubId)

    fun observeArchivedClubTasks(clubId: String): Flow<List<TaskEntity>> =
        dao.observeArchivedClubTasks(clubId)

    /** Архивировать задачу. purgeAfter = archivedAt + 90 дней. */
    suspend fun archiveTask(id: String) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val purgeAfter = now + 90L * 24 * 3600 * 1000
            dao.archive(id, now, purgeAfter)
            dao.getTask(id)?.let { syncQueueManager.enqueue("task", id, "UPDATE", it) }
        }
    }

    suspend fun restoreTask(id: String) {
        db.withTransaction {
            dao.restore(id)
            dao.getTask(id)?.let { syncQueueManager.enqueue("task", id, "UPDATE", it) }
        }
    }

    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): Int {
        return db.withTransaction { dao.purgeExpired(now) }
    }
    fun observeCreatedTasks(userId: String): Flow<List<TaskEntity>> = dao.observeCreatedTasks(userId)
    fun observeEventTasks(eventId: String): Flow<List<TaskEntity>> = dao.observeEventTasks(eventId)

    suspend fun upsert(task: TaskEntity) {
        db.withTransaction {
            dao.upsert(task)
            syncQueueManager.enqueue("task", task.id, "CREATE", task)
        }
    }

    suspend fun update(task: TaskEntity) {
        db.withTransaction {
            dao.update(task)
            syncQueueManager.enqueue("task", task.id, "UPDATE", task)
        }
    }

    suspend fun updateStatus(id: String, status: String) {
        db.withTransaction {
            dao.updateStatus(id, status)
            dao.getTask(id)?.let { syncQueueManager.enqueue("task", id, "UPDATE", it) }
        }
    }

    suspend fun delete(task: TaskEntity) {
        db.withTransaction {
            dao.delete(task)
            syncQueueManager.enqueue("task", task.id, "DELETE", task)
        }
    }

    suspend fun upsertAssignment(a: AssignmentEntity) {
        db.withTransaction { dao.upsertAssignment(a) }
    }

    suspend fun getAssignments(taskId: String): List<AssignmentEntity> = dao.getAssignments(taskId)

    // ---------- A1: Группы и per-user статус ----------

    /**
     * Задачи пользователя: через assignments (per-user статус).
     * Используется в MyTasksScreen.
     */
    fun observeMyTasks(userId: String): Flow<List<TaskEntity>> =
        dao.observeAssignedViaAssignments(userId)

    fun observeMyArchivedTasks(userId: String): Flow<List<TaskEntity>> =
        dao.observeArchivedViaAssignments(userId)

    /**
     * Создать задачу для группы.
     * @param task        задача (assignedTo = "", targetGroup = код группы)
     * @param memberIds   список userId, попавших в группу
     */
    suspend fun createTaskForGroup(task: TaskEntity, memberIds: List<String>): Result<Unit> {
        if (memberIds.isEmpty()) {
            return Result.failure(IllegalStateException("В группе нет участников"))
        }
        return try {
            db.withTransaction {
                dao.upsert(task)
                memberIds.forEach { uid ->
                    dao.upsertAssignment(
                        AssignmentEntity(
                            id = "${task.id}_$uid",
                            taskId = task.id,
                            userId = uid,
                            role = null,
                            status = "assigned",
                            acceptedAt = null,
                            completedAt = null,
                            confirmedBy = null,
                            confirmedAt = null,
                            pendingSync = true,
                        )
                    )
                }
                syncQueueManager.enqueue("task", task.id, "CREATE", task)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Per-user статус. Обновляем только assignment, не задачу. */
    suspend fun acceptAssignment(taskId: String, userId: String) {
        db.withTransaction {
            dao.updateAssignmentStatus(taskId, userId, "accepted", System.currentTimeMillis())
        }
    }

    suspend fun completeAssignment(taskId: String, userId: String) {
        db.withTransaction {
            dao.completeAssignment(taskId, userId, "done", System.currentTimeMillis())
        }
    }

    suspend fun getAssignment(taskId: String, userId: String): AssignmentEntity? =
        dao.getAssignment(taskId, userId)

    suspend fun upsertConfirmation(c: ConfirmationEntity) {
        db.withTransaction { dao.upsertConfirmation(c) }
    }

    fun observePendingConfirmations(): Flow<List<ConfirmationEntity>> = dao.observePendingConfirmations()
}