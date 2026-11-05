package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.offline.SyncConflictEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface SyncConflictDao {

    @Query("SELECT * FROM sync_conflicts WHERE userId = :userId AND status = 'pending' ORDER BY localUpdatedAt DESC")
    fun observeUnresolved(userId: String): Flow<List<SyncConflictEntity>>

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE userId = :userId AND status = 'pending'")
    fun observeUnresolvedCount(userId: String): Flow<Int>

    @Query("SELECT * FROM sync_conflicts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SyncConflictEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conflict: SyncConflictEntity)

    @Query("UPDATE sync_conflicts SET status = :status, resolvedBy = :resolvedBy, resolvedAt = :resolvedAt, resolution = :resolution WHERE id = :id")
    suspend fun updateResolution(
        id: String,
        status: String,
        resolvedBy: String?,
        resolvedAt: Date?,
        resolution: String?,
    )

    @Query("DELETE FROM sync_conflicts WHERE id = :id")
    suspend fun deleteById(id: String)
}
