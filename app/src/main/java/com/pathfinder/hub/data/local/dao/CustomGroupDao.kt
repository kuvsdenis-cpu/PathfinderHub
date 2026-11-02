package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomGroupDao {

    @Query("SELECT * FROM custom_groups WHERE clubId = :clubId AND archivedAt IS NULL ORDER BY name")
    fun observeActiveByClub(clubId: String): Flow<List<CustomGroupEntity>>

    @Query("SELECT * FROM custom_groups WHERE clubId = :clubId AND archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchivedByClub(clubId: String): Flow<List<CustomGroupEntity>>

    @Query("SELECT * FROM custom_groups WHERE id = :id")
    suspend fun getById(id: String): CustomGroupEntity?

    @Query("SELECT * FROM custom_groups WHERE clubId = :clubId AND name = :name AND archivedAt IS NULL LIMIT 1")
    suspend fun findByName(clubId: String, name: String): CustomGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(g: CustomGroupEntity)

    @Query("DELETE FROM custom_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE custom_groups SET archivedAt = :at, purgeAfter = :purgeAfter WHERE id = :id")
    suspend fun archive(id: String, at: Long, purgeAfter: Long)

    @Query("UPDATE custom_groups SET archivedAt = NULL, purgeAfter = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM custom_groups WHERE purgeAfter IS NOT NULL AND purgeAfter < :now")
    suspend fun purgeExpired(now: Long): Int

    /**
     * Резолв участников по правилу. SQL собирается динамически
     * в CustomGroupRepository на основе CustomGroupRule.
     */
    @RawQuery(observedEntities = [
        com.pathfinder.hub.data.local.entity.core.UserEntity::class,
        com.pathfinder.hub.data.local.entity.core.UserClubEntity::class,
        com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity::class,
        com.pathfinder.hub.data.local.entity.learning.HonorCompletionEntity::class,
    ])
    suspend fun resolveMembers(query: SupportSQLiteQuery): List<String>
}
