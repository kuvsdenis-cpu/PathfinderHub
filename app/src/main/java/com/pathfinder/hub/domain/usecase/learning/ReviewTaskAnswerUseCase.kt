package com.pathfinder.hub.domain.usecase.learning

import com.pathfinder.hub.data.repository.TaskAnswerRepository
import com.pathfinder.hub.data.repository.UserRepository
import javax.inject.Inject

/**
 * Проверка ответа наставником или директором.
 *
 * Права:
 *   - director — всегда
 *   - instructor / secretary — только свой клуб
 *   - остальные — запрещено
 */
class ReviewTaskAnswerUseCase @Inject constructor(
    private val repo: TaskAnswerRepository,
    private val userRepository: UserRepository,
) {

    suspend fun approve(
        clubId: String,
        targetUserId: String,
        taskId: String,
        reviewerId: String,
    ): Result<Unit> {
        if (!canReview(clubId, reviewerId)) {
            return Result.failure(SecurityException("Нет прав на проверку"))
        }
        return repo.approve(clubId, targetUserId, taskId, reviewerId)
    }

    suspend fun reject(
        clubId: String,
        targetUserId: String,
        taskId: String,
        reviewerId: String,
        comment: String,
    ): Result<Unit> {
        if (!canReview(clubId, reviewerId)) {
            return Result.failure(SecurityException("Нет прав на проверку"))
        }
        return repo.reject(clubId, targetUserId, taskId, reviewerId, comment)
    }

    private suspend fun canReview(clubId: String, userId: String): Boolean {
        val user = userRepository.getUser(userId) ?: return false
        return when (user.role) {
            "director" -> true
            "instructor", "secretary" -> user.clubId == clubId
            else -> false
        }
    }
}
