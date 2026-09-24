package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pathfinder.hub.data.local.entity.learning.LeaderChecklistEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity
import com.pathfinder.hub.data.local.relation.LevelWithSections
import kotlinx.coroutines.flow.Flow

@Dao
interface LevelDao {

    @Query("SELECT * FROM levels ORDER BY `order`")
    fun observeLevels(): Flow<List<LevelEntity>>

    @Transaction
    @Query("SELECT * FROM levels ORDER BY `order`")
    fun observeLevelsWithSections(): Flow<List<LevelWithSections>>

    @Query("SELECT * FROM levels WHERE id = :id")
    suspend fun getLevel(id: String): LevelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLevels(levels: List<LevelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLevel(level: LevelEntity)

    @Query("SELECT * FROM level_sections WHERE levelId = :levelId ORDER BY `order`")
    fun observeSections(levelId: String): Flow<List<LevelSectionEntity>>

    @Query("SELECT * FROM level_sections WHERE levelId = :levelId ORDER BY `order`")
    suspend fun getSections(levelId: String): List<LevelSectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSections(sections: List<LevelSectionEntity>)

    @Query("SELECT * FROM level_requirements WHERE levelId = :levelId ORDER BY `order`")
    fun observeRequirements(levelId: String): Flow<List<LevelRequirementEntity>>

    @Query("SELECT * FROM level_requirements WHERE levelId = :levelId ORDER BY `order`")
    suspend fun getRequirements(levelId: String): List<LevelRequirementEntity>

    @Query("SELECT * FROM level_requirements WHERE sectionId = :sectionId ORDER BY `order`")
    suspend fun getRequirementsBySection(sectionId: String): List<LevelRequirementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequirements(reqs: List<LevelRequirementEntity>)

    @Query("SELECT * FROM level_progress WHERE userId = :userId AND levelId = :levelId")
    fun observeProgress(userId: String, levelId: String): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM level_progress WHERE userId = :userId AND levelId = :levelId")
    suspend fun getProgressSnapshot(userId: String, levelId: String): List<LevelProgressEntity>

    @Query("SELECT * FROM level_progress WHERE userId = :userId AND requirementId = :reqId LIMIT 1")
    suspend fun getProgressForRequirement(userId: String, reqId: String): LevelProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: LevelProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgressList(items: List<LevelProgressEntity>)

    @Update
    suspend fun updateProgress(p: LevelProgressEntity)

    @Query("UPDATE level_progress SET status = :status, approvedBy = :by, approvedAt = :at WHERE userId = :userId AND requirementId = :reqId")
    suspend fun approveRequirement(userId: String, reqId: String, status: String, by: String, at: Long)

    @Query("DELETE FROM level_progress WHERE userId = :userId AND requirementId = :requirementId")
    suspend fun clearProgressForRequirement(userId: String, requirementId: String)

    @Query("DELETE FROM level_progress WHERE userId = :userId AND levelId = :levelId")
    suspend fun clearProgress(userId: String, levelId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(c: LevelCompletionEntity)

    @Query("SELECT * FROM level_completions WHERE userId = :userId")
    fun observeCompletions(userId: String): Flow<List<LevelCompletionEntity>>

    @Query("SELECT * FROM level_completions WHERE userId = :userId")
    suspend fun getCompletions(userId: String): List<LevelCompletionEntity>

    @Query("SELECT * FROM level_completions WHERE userId = :userId AND levelId = :levelId")
    suspend fun getCompletion(userId: String, levelId: String): LevelCompletionEntity?

    @Delete
    suspend fun deleteCompletion(c: LevelCompletionEntity)

    @Query("SELECT * FROM leader_checklists WHERE leaderId = :leaderId AND childId = :childId")
    fun observeChecklist(leaderId: String, childId: String): Flow<LeaderChecklistEntity?>

    @Query("SELECT * FROM leader_checklists WHERE leaderId = :leaderId AND childId = :childId AND levelId = :levelId LIMIT 1")
    suspend fun getChecklist(leaderId: String, childId: String, levelId: String): LeaderChecklistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklist(c: LeaderChecklistEntity)

    @Query("SELECT * FROM leader_checklists WHERE childId = :childId")
    suspend fun getChecklistsForChild(childId: String): List<LeaderChecklistEntity>
}
