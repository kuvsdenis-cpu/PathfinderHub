package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.groups.CustomGroupExclusionEntity

@Dao
interface CustomGroupExclusionDao {

    @Query("SELECT userId FROM custom_group_exclusions WHERE groupId = :groupId")
    suspend fun excludedUserIds(groupId: String): List<String>

    @Query("SELECT * FROM custom_group_exclusions WHERE groupId = :groupId AND userId = :userId")
    suspend fun getExclusion(groupId: String, userId: String): CustomGroupExclusionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: CustomGroupExclusionEntity)

    @Query("DELETE FROM custom_group_exclusions WHERE groupId = :groupId AND userId = :userId")
    suspend fun delete(groupId: String, userId: String)

    @Query("SELECT * FROM custom_group_exclusions WHERE pendingSync = 1")
    suspend fun pending(): List<CustomGroupExclusionEntity>

    @Query("UPDATE custom_group_exclusions SET pendingSync = 0 WHERE groupId = :groupId AND userId = :userId")
    suspend fun markSynced(groupId: String, userId: String)
}
