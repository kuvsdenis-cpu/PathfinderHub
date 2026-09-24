package com.pathfinder.hub.domain.usecase.learning
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.local.entity.learning.VerseProgressEntity
import javax.inject.Inject

class MarkVerseUseCase @Inject constructor(private val honorRepo: HonorRepository) {
    suspend operator fun invoke(u: String, v: String, status: String): Result<Unit> = try {
        honorRepo.upsertVerseProgress(VerseProgressEntity(
            userId = u, verseId = v, status = status,
            approvedBy = null, approvedAt = null, pendingSync = true
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
