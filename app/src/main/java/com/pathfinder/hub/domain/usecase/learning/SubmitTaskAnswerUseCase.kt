package com.pathfinder.hub.domain.usecase.learning

import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.repository.TaskAnswerRepository
import javax.inject.Inject

/**
 * Отправка ответа следопытом.
 *
 * Для заданий с autoCheck=true и совпадающим correctAnswer → auto_approved.
 * Иначе → submitted (ждёт ручной проверки).
 */
class SubmitTaskAnswerUseCase @Inject constructor(
    private val repo: TaskAnswerRepository,
) {
    suspend operator fun invoke(
        clubId: String,
        userId: String,
        requirementId: String,
        taskId: String,
        answer: String,
        attachments: List<String>,
        autoCheck: Boolean,
        correctAnswer: String?,
    ): Result<RequirementTaskAnswerEntity> {
        return repo.submit(
            clubId = clubId,
            userId = userId,
            requirementId = requirementId,
            taskId = taskId,
            answer = answer,
            attachments = attachments,
            autoCheck = autoCheck,
            correctAnswer = correctAnswer,
        )
    }
}
