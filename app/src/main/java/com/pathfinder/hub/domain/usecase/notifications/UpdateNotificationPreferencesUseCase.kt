package com.pathfinder.hub.domain.usecase.notifications
import com.pathfinder.hub.data.repository.NotificationRepository
import com.pathfinder.hub.data.local.entity.notifications.NotificationPreferencesEntity
import javax.inject.Inject

class UpdateNotificationPreferencesUseCase @Inject constructor(private val repo: NotificationRepository) {
    suspend operator fun invoke(p: NotificationPreferencesEntity): Result<Unit> = try {
        repo.upsertPrefs(p)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
