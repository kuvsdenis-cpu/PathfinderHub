package com.pathfinder.hub.domain.usecase.planning
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.SyncRepository
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import com.google.gson.Gson
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class UpdateEventUseCase @Inject constructor(
    private val eventRepo: EventRepository,
    private val syncRepo: SyncRepository
) {
    private val gson = Gson()
    suspend operator fun invoke(e: EventEntity): Result<Unit> = try {
        val entity = e.copy(status = "pending", pendingSync = true)
        eventRepo.update(entity)
        syncRepo.upsertItem(SyncQueueItemEntity(
            id = UUID.randomUUID().toString(), userId = e.createdBy,
            operation = "update", entityType = "event", entityId = e.id,
            payload = gson.toJson(entity), status = "pending", retryCount = 0,
            lastAttemptAt = null, errorMessage = null,
            createdAt = Date(), priority = "normal"
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
