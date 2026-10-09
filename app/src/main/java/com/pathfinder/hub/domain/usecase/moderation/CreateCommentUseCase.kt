package com.pathfinder.hub.domain.usecase.moderation

import android.util.Log
import com.google.gson.Gson
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import com.pathfinder.hub.data.local.entity.planning.CommentEntity
import com.pathfinder.hub.data.repository.ModerationRepository
import com.pathfinder.hub.data.repository.SyncRepository
import com.pathfinder.hub.domain.service.AutoModerationService
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class CreateCommentUseCase @Inject constructor(
    private val moderationRepo: ModerationRepository,
    private val syncRepo: SyncRepository,
    private val autoModerationService: AutoModerationService
) {
    companion object {
        private const val TAG = "CreateCommentUC"
        private const val CLUB_CHAT_TARGET = "club_chat"
    }

    private val gson = Gson()

    suspend operator fun invoke(c: CommentEntity): Result<String> = try {
        val id = c.id.ifBlank { UUID.randomUUID().toString() }

        // Определяем clubId: берём из entity, если пусто — из targetId для чата
        val clubId = c.clubId.ifBlank {
            if (c.targetType == CLUB_CHAT_TARGET) c.targetId else ""
        }

        if (clubId.isBlank()) {
            Log.w(TAG, "clubId пустой — комментарий не уйдёт в Firestore")
        }

        // Авто-модерация
        val auto = autoModerationService.check(c.text)

        val status = when (auto) {
            AutoModerationService.RESULT_PASSED -> "published"
            AutoModerationService.RESULT_FLAGGED -> "pending_review"
            AutoModerationService.RESULT_BLOCKED -> "rejected"
            else -> "pending_review"
        }

        val entity = c.copy(
            id = id,
            clubId = clubId,
            status = status,
            autoModerationResult = auto,
            publishedAt = if (auto == AutoModerationService.RESULT_PASSED) Date() else null,
            reviewDeadline = if (auto == AutoModerationService.RESULT_PASSED)
                Date(System.currentTimeMillis() + 6 * 3600 * 1000) else null,
            pendingSync = true
        )

        // Записываем через репозиторий (Firestore + Room)
        moderationRepo.sendComment(entity)

        // Дублируем в очередь — на случай, если Firestore был недоступен
        syncRepo.upsertItem(
            SyncQueueItemEntity(
                id = UUID.randomUUID().toString(),
                userId = c.authorId,
                operation = "create",
                entityType = "comment",
                entityId = id,
                payload = gson.toJson(entity),
                status = "pending",
                retryCount = 0,
                lastAttemptAt = null,
                errorMessage = null,
                createdAt = Date(),
                priority = "normal"
            )
        )

        Result.success(id)
    } catch (e: Exception) {
        Log.e(TAG, "createComment failed", e)
        Result.failure(e)
    }
}