package com.pathfinder.hub.domain.usecase.learning

import com.pathfinder.hub.data.local.dao.RequirementTaskDao
import com.pathfinder.hub.data.repository.TaskAnswerRepository
import javax.inject.Inject

/**
 * Проверяет, все ли обязательные задания требования выполнены.
 *
 * Выполнено, если:
 *   для каждого isRequired=true задания есть ответ
 *   в статусе approved или auto_approved.
 */
class CheckRequirementCompletionUseCase @Inject constructor(
    private val taskDao: RequirementTaskDao,
    private val answerRepo: TaskAnswerRepository,
) {

    suspend operator fun invoke(
        clubId: String,
        userId: String,
        requirementId: String,
    ): Boolean {
        val requiredTasks = taskDao.getRequiredForRequirement(requirementId, clubId)
            .filter { it.clubId == clubId }

        if (requiredTasks.isEmpty()) return true

        val approvedCount = answerRepo.countApproved(requirementId, userId)
        return approvedCount >= requiredTasks.size
    }
}
