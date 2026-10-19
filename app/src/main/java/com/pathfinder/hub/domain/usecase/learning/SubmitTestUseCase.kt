package com.pathfinder.hub.domain.usecase.learning

import com.pathfinder.hub.data.local.entity.learning.OpenAnswerReviewEntity
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import com.pathfinder.hub.data.repository.TestRepository
import java.util.Date
import javax.inject.Inject

class SubmitTestUseCase @Inject constructor(private val testRepo: TestRepository) {

    /**
     * Сохраняет попытку теста.
     *
     * @param attemptId  UUID, сгенерированный во ViewModel (нужен для OpenAnswerReviewEntity)
     * @param earnedPoints  баллы за choice-верные (long_text пока 0)
     * @param totalPoints   сумма points всех вопросов
     * @param choiceAnswers  JSON-строка {questionId: answer} для choice
     * @param openAnswers    (questionId, answer) для long_text
     */
    suspend operator fun invoke(
        u: String,
        attemptId: String,
        testId: String,
        earnedPoints: Int,
        totalPoints: Int,
        passScore: Int,
        choiceAnswers: String,
        openAnswers: List<Pair<String, String>> = emptyList(),
    ): Result<Boolean> = try {
        val score = if (totalPoints > 0) earnedPoints * 100 / totalPoints else 0
        val passed = score >= passScore
        val num = (testRepo.getLastAttemptNumber(u, testId) ?: 0) + 1

        testRepo.upsertAttempt(TestAttemptEntity(
            id = attemptId,
            userId = u,
            honorId = "",
            testId = testId,
            score = score,
            passed = passed,
            attemptNumber = num,
            startedAt = Date(),
            completedAt = Date(),
            pendingSync = true,
            choiceAnswers = choiceAnswers,
        ))

        openAnswers.forEach { (questionId, answer) ->
            testRepo.upsertOpenAnswer(OpenAnswerReviewEntity(
                attemptId = attemptId,
                questionId = questionId,
                answer = answer,
                score = null,
                reviewedBy = null,
                reviewedAt = null,
            ))
        }

        Result.success(passed)
    } catch (e: Exception) { Result.failure(e) }
}
