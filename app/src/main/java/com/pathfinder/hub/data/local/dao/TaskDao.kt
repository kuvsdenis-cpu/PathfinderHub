package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.planning.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeTask(id: String): Flow<TaskEntity?>
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: String): TaskEntity?
    @Query("SELECT * FROM tasks WHERE clubId = :clubId ORDER BY createdAt DESC")
    fun observeClubTasks(clubId: String): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE assignedTo = :userId ORDER BY dueDate")
    fun observeAssignedTasks(userId: String): Flow<List<TaskEntity>>

    // ---------- Архивация ----------
    @Query("SELECT * FROM tasks WHERE assignedTo = :userId AND archivedAt IS NULL ORDER BY dueDate")
    fun observeActiveAssignedTasks(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE assignedTo = :userId AND archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchivedAssignedTasks(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE clubId = :clubId AND archivedAt IS NULL ORDER BY createdAt DESC")
    fun observeActiveClubTasks(clubId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE clubId = :clubId AND archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchivedClubTasks(clubId: String): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET archivedAt = :at, purgeAfter = :purgeAfter WHERE id = :id")
    suspend fun archive(id: String, at: Long, purgeAfter: Long)

    @Query("UPDATE tasks SET archivedAt = NULL, purgeAfter = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM tasks WHERE purgeAfter IS NOT NULL AND purgeAfter < :now")
    suspend fun purgeExpired(now: Long): Int
    @Query("SELECT * FROM tasks WHERE assignedBy = :userId ORDER BY createdAt DESC")
    fun observeCreatedTasks(userId: String): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE eventId = :eventId")
    fun observeEventTasks(eventId: String): Flow<List<TaskEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)
    @Update suspend fun update(task: TaskEntity)
    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
    @Delete suspend fun delete(task: TaskEntity)
    @Query("SELECT COUNT(*) FROM tasks WHERE assignedTo = :userId AND status IN ('done', 'confirmed')")
    suspend fun countCompletedByUser(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(a: AssignmentEntity)
    @Query("SELECT * FROM assignments WHERE taskId = :taskId")
    suspend fun getAssignments(taskId: String): List<AssignmentEntity>

    // ---------- A1: Группы и per-user статус ----------

    /**
     * Задачи, назначенные конкретному пользователю через AssignmentEntity.
     * Возвращает задачи с per-user статусом (из assignment).
     */
    @Query("""
        SELECT t.* FROM tasks t
        INNER JOIN assignments a ON a.taskId = t.id
        WHERE a.userId = :userId
          AND t.archivedAt IS NULL
        ORDER BY t.dueDate
    """)
    fun observeAssignedViaAssignments(userId: String): Flow<List<TaskEntity>>

    @Query("""
        SELECT t.* FROM tasks t
        INNER JOIN assignments a ON a.taskId = t.id
        WHERE a.userId = :userId
          AND t.archivedAt IS NOT NULL
        ORDER BY t.archivedAt DESC
    """)
    fun observeArchivedViaAssignments(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM assignments WHERE taskId = :taskId AND userId = :userId LIMIT 1")
    suspend fun getAssignment(taskId: String, userId: String): AssignmentEntity?

    @Query("UPDATE assignments SET status = :status, acceptedAt = :at WHERE taskId = :taskId AND userId = :userId")
    suspend fun updateAssignmentStatus(taskId: String, userId: String, status: String, at: Long?)

    @Query("UPDATE assignments SET status = :status, completedAt = :at WHERE taskId = :taskId AND userId = :userId")
    suspend fun completeAssignment(taskId: String, userId: String, status: String, at: Long?)

    @Query("DELETE FROM assignments WHERE taskId = :taskId")
    suspend fun deleteAssignments(taskId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfirmation(c: ConfirmationEntity)
    @Query("SELECT * FROM confirmations WHERE status = 'pending'")
    fun observePendingConfirmations(): Flow<List<ConfirmationEntity>>
}
