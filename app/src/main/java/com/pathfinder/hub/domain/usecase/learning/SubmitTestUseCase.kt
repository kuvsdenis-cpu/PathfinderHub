package com.pathfinder.hub.domain.usecase.learning
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class SubmitTestUseCase @Inject constructor(private val honorRepo: HonorRepository) {
    suspend operator fun invoke(u: String, h: String, correct: Int, total: Int): Result<Boolean> = try {
        val score = if (total > 0) (correct * 100) / total else 0
        val passed = score >= 90
        val num = (honorRepo.getLastAttemptNumber(u, h) ?: 0) + 1
        honorRepo.upsertAttempt(TestAttemptEntity(
            id = UUID.randomUUID().toString(), userId = u, honorId = h,
            score = score, passed = passed, attemptNumber = num,
            startedAt = Date(), completedAt = Date(), pendingSync = true
        ))
        Result.success(passed)
    } catch (e: Exception) { Result.failure(e) }
}
