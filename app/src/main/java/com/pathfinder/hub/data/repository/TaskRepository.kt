package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.TaskDao
import com.pathfinder.hub.data.local.entity.planning.AssignmentEntity
import com.pathfinder.hub.data.local.entity.planning.ConfirmationEntity
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val syncQueueManager: SyncQueueManager
) {
    fun observeTask(id: String): Flow<TaskEntity?> = dao.observeTask(id)
    suspend fun getTask(id: String): TaskEntity? = dao.getTask(id)
    fun observeClubTasks(clubId: String): Flow<List<TaskEntity>> = dao.observeClubTasks(clubId)
    fun observeAssignedTasks(userId: String): Flow<List<TaskEntity>> = dao.observeAssignedTasks(userId)
    fun observeCreatedTasks(userId: String): Flow<List<TaskEntity>> = dao.observeCreatedTasks(userId)
    fun observeEventTasks(eventId: String): Flow<List<TaskEntity>> = dao.observeEventTasks(eventId)

    suspend fun upsert(task: TaskEntity) {
        dao.upsert(task)
        syncQueueManager.enqueue("task", task.id, "CREATE", task)
    }

    suspend fun update(task: TaskEntity) {
        dao.update(task)
        syncQueueManager.enqueue("task", task.id, "UPDATE", task)
    }

    suspend fun updateStatus(id: String, status: String) {
        dao.updateStatus(id, status)
        dao.getTask(id)?.let { syncQueueManager.enqueue("task", id, "UPDATE", it) }
    }

    suspend fun delete(task: TaskEntity) {
        dao.delete(task)
        syncQueueManager.enqueue("task", task.id, "DELETE", task)
    }

    suspend fun upsertAssignment(a: AssignmentEntity) = dao.upsertAssignment(a)
    suspend fun getAssignments(taskId: String): List<AssignmentEntity> = dao.getAssignments(taskId)
    suspend fun upsertConfirmation(c: ConfirmationEntity) = dao.upsertConfirmation(c)
    fun observePendingConfirmations(): Flow<List<ConfirmationEntity>> = dao.observePendingConfirmations()
}