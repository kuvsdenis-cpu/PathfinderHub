package com.pathfinder.hub.domain.usecase.planning

import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.SyncRepository
import com.google.gson.Gson
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class DeleteEventUseCase @Inject constructor(
    private val eventRepo: EventRepository,
    private val syncRepo: SyncRepository
) {
    private val gson = Gson()

    suspend operator fun invoke(e: EventEntity): Result<Unit> = try {
        eventRepo.delete(e)
        syncRepo.upsertItem(
            SyncQueueItemEntity(
                id = UUID.randomUUID().toString(),
                userId = e.createdBy,
                operation = "delete",
                entityType = "event",
                entityId = e.id,
                payload = gson.toJson(e),
                status = "pending",
                retryCount = 0,
                lastAttemptAt = null,
                errorMessage = null,
                createdAt = Date(),
                priority = "normal"
            )
        )
        Result.success(Unit)
    } catch (ex: Exception) {
        Result.failure(ex)
    }
}