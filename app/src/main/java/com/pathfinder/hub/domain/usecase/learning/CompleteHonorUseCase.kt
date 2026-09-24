package com.pathfinder.hub.domain.usecase.learning
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.local.entity.learning.HonorCompletionEntity
import java.util.Date
import javax.inject.Inject

class CompleteHonorUseCase @Inject constructor(private val honorRepo: HonorRepository) {
    suspend operator fun invoke(u: String, h: String, approver: String): Result<Unit> = try {
        honorRepo.upsertCompletion(HonorCompletionEntity(
            userId = u, honorId = h, completedAt = Date(), approvedBy = approver
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
