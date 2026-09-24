package com.pathfinder.hub.data.repository
import com.pathfinder.hub.data.local.dao.SyncDao
import com.pathfinder.hub.data.local.entity.offline.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SyncRepository @Inject constructor(private val dao: SyncDao) {
    suspend fun getNextBatch(l: Int = 50) = dao.getNextBatch(l)
    suspend fun upsertItem(i: SyncQueueItemEntity) = dao.upsertItem(i)
    suspend fun updateItemStatus(id: String, s: String, r: Int, at: Long, e: String?) =
        dao.updateItemStatus(id, s, r, at, e)
    suspend fun deleteItem(id: String) = dao.deleteItem(id)
    suspend fun purgeFailed(b: Long) = dao.purgeFailed(b)
    fun observeQueueSize(): Flow<Int> = dao.observeQueueSize()
    fun observeQueue(): Flow<List<SyncQueueItemEntity>> = dao.observeQueue()
    fun observePendingConflicts(): Flow<List<SyncConflictEntity>> = dao.observePendingConflicts()
    suspend fun upsertConflict(c: SyncConflictEntity) = dao.upsertConflict(c)
    suspend fun resolveConflict(id: String, b: String, at: Long, r: String) = dao.resolveConflict(id, b, at, r)
    suspend fun getPendingMedia(): List<MediaSyncQueueEntity> = dao.getPendingMedia()
    suspend fun upsertMedia(m: MediaSyncQueueEntity) = dao.upsertMedia(m)
    suspend fun updateMediaStatus(id: String, s: String, r: Int) = dao.updateMediaStatus(id, s, r)
    suspend fun upsertCacheMetadata(m: CacheMetadataEntity) = dao.upsertCacheMetadata(m)
    suspend fun getAllCacheMetadata(): List<CacheMetadataEntity> = dao.getAllCacheMetadata()
    suspend fun deleteCache(k: String) = dao.deleteCache(k)
    fun observeCacheSettings(): Flow<CacheSettingsEntity?> = dao.observeCacheSettings()
    suspend fun upsertCacheSettings(s: CacheSettingsEntity) = dao.upsertCacheSettings(s)
    fun observeSettings(): Flow<SyncSettingsEntity?> = dao.observeSettings()
    suspend fun upsertSettings(s: SyncSettingsEntity) = dao.upsertSettings(s)
    fun observeStatus(): Flow<SyncStatusEntity?> = dao.observeStatus()
    suspend fun upsertStatus(s: SyncStatusEntity) = dao.upsertStatus(s)
    suspend fun getMergeRule(t: String): MergeRuleEntity? = dao.getMergeRule(t)
    suspend fun upsertMergeRules(r: List<MergeRuleEntity>) = dao.upsertMergeRules(r)
}
