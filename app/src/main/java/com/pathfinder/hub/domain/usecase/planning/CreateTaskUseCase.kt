package com.pathfinder.hub.domain.usecase.planning
import com.pathfinder.hub.data.repository.TaskRepository
import com.pathfinder.hub.data.repository.SyncRepository
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import com.pathfinder.hub.data.local.entity.audit.AuditLogEntity
import com.google.gson.Gson
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class CreateTaskUseCase @Inject constructor(
    private val taskRepo: TaskRepository,
    private val syncRepo: SyncRepository,
    private val auditRepo: AuditRepository,
) {
    private val gson = Gson()
    suspend operator fun invoke(t: TaskEntity): Result<String> = try {
        val id = t.id.ifBlank { UUID.randomUUID().toString() }
        val entity = t.copy(id = id, status = "assigned", pendingSync = true)
        taskRepo.upsert(entity)
        syncRepo.upsertItem(SyncQueueItemEntity(
            id = UUID.randomUUID().toString(), userId = t.assignedBy,
            operation = "create", entityType = "task", entityId = id,
            payload = gson.toJson(entity), status = "pending", retryCount = 0,
            lastAttemptAt = null, errorMessage = null,
            createdAt = Date(), priority = "high"
        ))
        runCatching {
            auditRepo.log(AuditLogEntity(
                id = UUID.randomUUID().toString(), targetType = "task", targetId = id,
                action = "create", userId = t.assignedBy, userRole = "",
                clubId = entity.clubId, conferenceId = null, timestamp = Date(),
                oldValue = null, newValue = gson.toJson(entity), reason = null,
                visibleToAuthor = true, ipHash = null, userAgent = null
            ))
        }
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }
}
