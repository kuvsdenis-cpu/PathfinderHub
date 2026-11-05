package com.pathfinder.hub.data.local.dao

import androidx.room.*
import com.pathfinder.hub.data.local.entity.planning.ModerationReasonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModerationReasonDao {

    @Query("SELECT * FROM moderation_reasons ORDER BY sortOrder, title")
    fun observeAll(): Flow<List<ModerationReasonEntity>>

    @Query("SELECT * FROM moderation_reasons WHERE isActive = 1 ORDER BY sortOrder, title")
    fun observeActive(): Flow<List<ModerationReasonEntity>>

    @Query("SELECT * FROM moderation_reasons WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): ModerationReasonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ModerationReasonEntity)

    @Query("DELETE FROM moderation_reasons WHERE code = :code AND isSystem = 0")
    suspend fun deleteCustom(code: String): Int

    @Query("SELECT COUNT(*) FROM moderation_reasons")
    suspend fun count(): Int
}
