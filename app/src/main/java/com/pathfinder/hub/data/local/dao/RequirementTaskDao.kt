package com.pathfinder.hub.data.local.dao

import androidx.room.*
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequirementTaskDao {

    @Query("""
        SELECT * FROM requirement_tasks
        WHERE requirementId = :requirementId
          AND clubId = :clubId
        ORDER BY orderIndex ASC
    """)
    fun observeForRequirement(requirementId: String, clubId: String): Flow<List<RequirementTaskEntity>>

    @Query("SELECT * FROM requirement_tasks WHERE requirementId = :requirementId")
    suspend fun getByRequirement(requirementId: String): List<RequirementTaskEntity>

    @Query("SELECT * FROM requirement_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RequirementTaskEntity?

    @Query("""
        SELECT * FROM requirement_tasks
        WHERE requirementId = :requirementId
          AND clubId = :clubId
          AND isRequired = 1
    """)
    suspend fun getRequiredForRequirement(requirementId: String, clubId: String): List<RequirementTaskEntity>

    @Query("SELECT * FROM requirement_tasks WHERE flaggedForEdit = 1 ORDER BY flaggedAt DESC")
    fun observeFlagged(): Flow<List<RequirementTaskEntity>>

    @Query("SELECT * FROM requirement_tasks WHERE requirementId = :requirementId AND clubId = :clubId ORDER BY orderIndex ASC")
    fun observeClubOverrides(requirementId: String, clubId: String): Flow<List<RequirementTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: RequirementTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<RequirementTaskEntity>)

    @Query("DELETE FROM requirement_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM requirement_tasks WHERE requirementId = :requirementId AND clubId = :clubId")
    suspend fun deleteByRequirementAndClub(requirementId: String, clubId: String)

    @Query("""
        UPDATE requirement_tasks
        SET flaggedForEdit = 1, flaggedBy = :by, flaggedAt = :at,
            flagComment = :comment, pendingSync = 1, updatedAt = :at
        WHERE id = :id
    """)
    suspend fun flag(id: String, by: String, at: Long, comment: String?)

    @Query("""
        UPDATE requirement_tasks
        SET flaggedForEdit = 0, flaggedBy = NULL, flaggedAt = NULL,
            flagComment = NULL, pendingSync = 1, updatedAt = :at
        WHERE id = :id
    """)
    suspend fun unflag(id: String, at: Long)

    @Query("SELECT * FROM requirement_tasks WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<RequirementTaskEntity>

    @Query("UPDATE requirement_tasks SET pendingSync = 0 WHERE id = :id")
    suspend fun clearPendingSync(id: String)
}
