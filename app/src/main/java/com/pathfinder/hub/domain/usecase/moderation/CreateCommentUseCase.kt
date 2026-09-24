package com.pathfinder.hub.domain.usecase.moderation
import com.pathfinder.hub.data.repository.ModerationRepository
import com.pathfinder.hub.data.repository.SyncRepository
import com.pathfinder.hub.data.local.entity.planning.CommentEntity
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import com.google.gson.Gson
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class CreateCommentUseCase @Inject constructor(
    private val moderationRepo: ModerationRepository,
    private val syncRepo: SyncRepository
) {
    private val gson = Gson()
    suspend operator fun invoke(c: CommentEntity): Result<String> = try {
        val id = c.id.ifBlank { UUID.randomUUID().toString() }
        val auto = autoModerate(c.text)
        val status = when (auto) {
            "passed" -> "published"
            "flagged" -> "pending_review"
            else -> "rejected"
        }
        val entity = c.copy(
            id = id, status = status, autoModerationResult = auto,
            publishedAt = if (auto == "passed") Date() else null,
            reviewDeadline = if (auto == "passed")
                Date(System.currentTimeMillis() + 6 * 3600 * 1000) else null,
            pendingSync = true
        )
        moderationRepo.upsertComment(entity)
        syncRepo.upsertItem(SyncQueueItemEntity(
            id = UUID.randomUUID().toString(), userId = c.authorId,
            operation = "create", entityType = "comment", entityId = id,
            payload = gson.toJson(entity), status = "pending", retryCount = 0,
            lastAttemptAt = null, errorMessage = null,
            createdAt = Date(), priority = "normal"
        ))
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }

    private fun autoModerate(text: String): String {
        val blocked = listOf("мат", "спам", "реклама")
        val flagged = listOf("http://", "https://", "@")
        val l = text.lowercase()
        return when {
            blocked.any { l.contains(it) } -> "blocked"
            flagged.any { l.contains(it) } -> "flagged"
            else -> "passed"
        }
    }
}
