package com.pathfinder.hub.domain.usecase.offline
import com.pathfinder.hub.data.repository.SyncRepository
import com.pathfinder.hub.data.local.entity.offline.SyncStatusEntity
import java.util.Date
import javax.inject.Inject

class SyncUseCase @Inject constructor(private val syncRepo: SyncRepository) {
    suspend operator fun invoke(): Result<Int> = try {
        syncRepo.upsertStatus(SyncStatusEntity(
            id = 1, isOnline = true, isSyncing = true, queueSize = 0,
            lastSyncAt = null, lastError = null, conflictsCount = 0
        ))
        val batch = syncRepo.getNextBatch(50)
        var processed = 0
        for (item in batch) {
            try {
                // TODO: отправить в Firestore
                syncRepo.deleteItem(item.id)
                processed++
            } catch (e: Exception) {
                syncRepo.updateItemStatus(
                    item.id, "failed", item.retryCount + 1,
                    System.currentTimeMillis(), e.message
                )
            }
        }
        syncRepo.upsertStatus(SyncStatusEntity(
            id = 1, isOnline = true, isSyncing = false, queueSize = 0,
            lastSyncAt = Date(), lastError = null, conflictsCount = 0
        ))
        Result.success(processed)
    } catch (e: Exception) { Result.failure(e) }
}
