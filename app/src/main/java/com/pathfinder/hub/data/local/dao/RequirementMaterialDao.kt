package com.pathfinder.hub.data.local.dao

import androidx.room.*
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequirementMaterialDao {

    @Query(
        """
        SELECT * FROM requirement_materials
        WHERE requirementId = :requirementId
          AND clubId = :clubId
        ORDER BY orderIndex ASC
        """
    )
    fun observeForRequirement(requirementId: String, clubId: String): Flow<List<RequirementMaterialEntity>>

    @Query("SELECT * FROM requirement_materials WHERE requirementId = :requirementId")
    suspend fun getByRequirement(requirementId: String): List<RequirementMaterialEntity>

    @Query("SELECT * FROM requirement_materials WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RequirementMaterialEntity?

    @Query("SELECT * FROM requirement_materials WHERE flaggedForEdit = 1 ORDER BY flaggedAt DESC")
    fun observeFlagged(): Flow<List<RequirementMaterialEntity>>

    @Query("SELECT * FROM requirement_materials WHERE requirementId = :requirementId AND clubId = :clubId ORDER BY orderIndex ASC")
    fun observeClubOverrides(requirementId: String, clubId: String): Flow<List<RequirementMaterialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(material: RequirementMaterialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(materials: List<RequirementMaterialEntity>)

    @Query("DELETE FROM requirement_materials WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM requirement_materials WHERE requirementId = :requirementId AND clubId = :clubId")
    suspend fun deleteByRequirementAndClub(requirementId: String, clubId: String)

    @Query("""
        UPDATE requirement_materials
        SET flaggedForEdit = 1, flaggedBy = :by, flaggedAt = :at,
            flagComment = :comment, pendingSync = 1, updatedAt = :at
        WHERE id = :id
    """)
    suspend fun flag(id: String, by: String, at: Long, comment: String?)

    @Query("""
        UPDATE requirement_materials
        SET flaggedForEdit = 0, flaggedBy = NULL, flaggedAt = NULL,
            flagComment = NULL, pendingSync = 1, updatedAt = :at
        WHERE id = :id
    """)
    suspend fun unflag(id: String, at: Long)

    @Query("SELECT * FROM requirement_materials WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<RequirementMaterialEntity>

    @Query("UPDATE requirement_materials SET pendingSync = 0 WHERE id = :id")
    suspend fun clearPendingSync(id: String)
}
