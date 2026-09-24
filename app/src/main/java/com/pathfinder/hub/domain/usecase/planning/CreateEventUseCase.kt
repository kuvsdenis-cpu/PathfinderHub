package com.pathfinder.hub.domain.usecase.planning
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.SyncRepository
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import com.pathfinder.hub.data.local.entity.audit.AuditLogEntity
import com.google.gson.Gson
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class CreateEventUseCase @Inject constructor(
    private val eventRepo: EventRepository,
    private val syncRepo: SyncRepository,
    private val auditRepo: AuditRepository
) {
    private val gson = Gson()
    suspend operator fun invoke(e: EventEntity): Result<String> = try {
        val id = e.id.ifBlank { UUID.randomUUID().toString() }
        val entity = e.copy(id = id, status = "pending", pendingSync = true)
        eventRepo.upsert(entity)
        syncRepo.upsertItem(SyncQueueItemEntity(
            id = UUID.randomUUID().toString(), userId = e.createdBy,
            operation = "create", entityType = "event", entityId = id,
            payload = gson.toJson(entity), status = "pending", retryCount = 0,
            lastAttemptAt = null, errorMessage = null,
            createdAt = Date(), priority = "normal"
        ))
        auditRepo.log(AuditLogEntity(
            id = UUID.randomUUID().toString(), targetType = "event", targetId = id,
            action = "create", userId = e.createdBy, userRole = "",
            clubId = e.clubId, conferenceId = null, timestamp = Date(),
            oldValue = null, newValue = gson.toJson(entity), reason = null,
            visibleToAuthor = true, ipHash = null, userAgent = null
        ))
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }
}
