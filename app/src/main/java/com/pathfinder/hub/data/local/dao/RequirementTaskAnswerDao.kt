package com.pathfinder.hub.data.local.dao

import androidx.room.*
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequirementTaskAnswerDao {

    @Query("SELECT * FROM requirement_task_answers WHERE requirementId = :requirementId AND userId = :userId")
    fun observeForUserAndRequirement(requirementId: String, userId: String): Flow<List<RequirementTaskAnswerEntity>>

    @Query("SELECT * FROM requirement_task_answers WHERE taskId = :taskId AND userId = :userId LIMIT 1")
    suspend fun getByTaskAndUser(taskId: String, userId: String): RequirementTaskAnswerEntity?

    @Query("SELECT * FROM requirement_task_answers WHERE taskId = :taskId AND userId = :userId LIMIT 1")
    fun observeByTaskAndUser(taskId: String, userId: String): Flow<RequirementTaskAnswerEntity?>

    @Query("SELECT * FROM requirement_task_answers WHERE clubId = :clubId AND status = 'submitted' ORDER BY submittedAt ASC")
    fun observeSubmittedForClub(clubId: String): Flow<List<RequirementTaskAnswerEntity>>

    @Query("""
        SELECT * FROM requirement_task_answers
        WHERE requirementId = :requirementId AND userId = :userId
          AND status IN ('approved', 'auto_approved')
    """)
    suspend fun getApprovedForRequirement(requirementId: String, userId: String): List<RequirementTaskAnswerEntity>

    @Query("""
        SELECT COUNT(*) FROM requirement_task_answers
        WHERE requirementId = :requirementId AND userId = :userId
          AND status IN ('approved', 'auto_approved')
    """)
    suspend fun countApprovedForRequirement(requirementId: String, userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(answer: RequirementTaskAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(answers: List<RequirementTaskAnswerEntity>)

    @Query("DELETE FROM requirement_task_answers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("""
        UPDATE requirement_task_answers
        SET answer = :answer, attachments = :attachments, status = :status,
            submittedAt = :submittedAt, updatedAt = :updatedAt, pendingSync = 1
        WHERE id = :id
    """)
    suspend fun updateAnswer(
        id: String, answer: String, attachments: String, status: String,
        submittedAt: Long?, updatedAt: Long
    )

    @Query("""
        UPDATE requirement_task_answers
        SET status = :status, reviewedBy = :by, reviewedAt = :at,
            reviewComment = :comment, pendingSync = 1, updatedAt = :at
        WHERE id = :id
    """)
    suspend fun review(id: String, status: String, by: String, at: Long, comment: String?)

    @Query("SELECT * FROM requirement_task_answers WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<RequirementTaskAnswerEntity>

    @Query("UPDATE requirement_task_answers SET pendingSync = 0 WHERE id = :id")
    suspend fun clearPendingSync(id: String)
}
