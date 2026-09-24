package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.offline.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("""SELECT * FROM sync_queue WHERE status = 'pending'
        ORDER BY CASE priority WHEN 'high' THEN 1 WHEN 'normal' THEN 2 ELSE 3 END,
        createdAt LIMIT :limit""")
    suspend fun getNextBatch(limit: Int = 50): List<SyncQueueItemEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: SyncQueueItemEntity)
    @Query("""UPDATE sync_queue SET status = :status, retryCount = :retry,
        lastAttemptAt = :at, errorMessage = :err WHERE id = :id""")
    suspend fun updateItemStatus(id: String, status: String, retry: Int, at: Long, err: String?)
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteItem(id: String)
    @Query("DELETE FROM sync_queue WHERE status = 'failed' AND createdAt < :before")
    suspend fun purgeFailed(before: Long)
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'")
    fun observeQueueSize(): Flow<Int>
    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC LIMIT 100")
    fun observeQueue(): Flow<List<SyncQueueItemEntity>>
    @Query("SELECT * FROM sync_conflicts WHERE status = 'pending'")
    fun observePendingConflicts(): Flow<List<SyncConflictEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflict(c: SyncConflictEntity)
    @Query("""UPDATE sync_conflicts SET status = 'resolved', resolvedBy = :by,
        resolvedAt = :at, resolution = :resolution WHERE id = :id""")
    suspend fun resolveConflict(id: String, by: String, at: Long, resolution: String)
    @Query("SELECT * FROM media_sync_queue WHERE status = 'pending' ORDER BY createdAt LIMIT 10")
    suspend fun getPendingMedia(): List<MediaSyncQueueEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(m: MediaSyncQueueEntity)
    @Query("UPDATE media_sync_queue SET status = :status, retryCount = :retry WHERE id = :id")
    suspend fun updateMediaStatus(id: String, status: String, retry: Int)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCacheMetadata(m: CacheMetadataEntity)
    @Query("SELECT * FROM cache_metadata")
    suspend fun getAllCacheMetadata(): List<CacheMetadataEntity>
    @Query("DELETE FROM cache_metadata WHERE key = :key")
    suspend fun deleteCache(key: String)
    @Query("SELECT * FROM cache_settings WHERE id = 1")
    fun observeCacheSettings(): Flow<CacheSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCacheSettings(s: CacheSettingsEntity)
    @Query("SELECT * FROM sync_settings WHERE id = 1")
    fun observeSettings(): Flow<SyncSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(s: SyncSettingsEntity)
    @Query("SELECT * FROM sync_status WHERE id = 1")
    fun observeStatus(): Flow<SyncStatusEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStatus(s: SyncStatusEntity)
    @Query("SELECT * FROM merge_rules WHERE entityType = :type")
    suspend fun getMergeRule(type: String): MergeRuleEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMergeRules(rules: List<MergeRuleEntity>)
}
