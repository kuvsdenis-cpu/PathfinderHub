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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(a: AssignmentEntity)
    @Query("SELECT * FROM assignments WHERE taskId = :taskId")
    suspend fun getAssignments(taskId: String): List<AssignmentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfirmation(c: ConfirmationEntity)
    @Query("SELECT * FROM confirmations WHERE status = 'pending'")
    fun observePendingConfirmations(): Flow<List<ConfirmationEntity>>
}
