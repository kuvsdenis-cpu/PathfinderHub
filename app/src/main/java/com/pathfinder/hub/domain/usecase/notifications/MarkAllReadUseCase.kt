package com.pathfinder.hub.domain.usecase.notifications
import com.pathfinder.hub.data.repository.NotificationRepository
import javax.inject.Inject

class MarkAllReadUseCase @Inject constructor(private val repo: NotificationRepository) {
    suspend operator fun invoke(userId: String): Result<Unit> = try {
        repo.markAllRead(userId, System.currentTimeMillis())
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
