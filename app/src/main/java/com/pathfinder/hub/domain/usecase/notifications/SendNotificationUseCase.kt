package com.pathfinder.hub.domain.usecase.notifications
import com.pathfinder.hub.data.repository.NotificationRepository
import com.pathfinder.hub.data.local.entity.notifications.NotificationEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class SendNotificationUseCase @Inject constructor(private val repo: NotificationRepository) {
    suspend operator fun invoke(
        userId: String, type: String, title: String, body: String,
        targetType: String? = null, targetId: String? = null,
        deepLink: String? = null, priority: String = "normal", groupId: String? = null
    ): Result<String> = try {
        val id = UUID.randomUUID().toString()
        repo.upsert(NotificationEntity(
            id = id, userId = userId, type = type, title = title, body = body,
            targetType = targetType, targetId = targetId, deepLink = deepLink,
            priority = priority, groupId = groupId,
            isRead = false, isHandled = false,
            createdAt = Date(), readAt = null, handledAt = null
        ))
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }
}
