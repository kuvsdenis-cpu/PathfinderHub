package com.pathfinder.hub.domain.usecase.learning
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class CompleteLevelUseCase @Inject constructor(private val levelRepo: LevelRepository) {
    suspend operator fun invoke(u: String, l: String, approver: String): Result<Unit> = try {
        levelRepo.upsertCompletion(LevelCompletionEntity(
            userId = u, levelId = l, completedAt = Date(), approvedBy = approver
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
