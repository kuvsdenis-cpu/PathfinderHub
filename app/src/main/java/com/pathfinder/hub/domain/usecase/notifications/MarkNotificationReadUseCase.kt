package com.pathfinder.hub.domain.usecase.notifications
import com.pathfinder.hub.data.repository.NotificationRepository
import javax.inject.Inject

class MarkNotificationReadUseCase @Inject constructor(private val repo: NotificationRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = try {
        repo.markRead(id, System.currentTimeMillis())
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
