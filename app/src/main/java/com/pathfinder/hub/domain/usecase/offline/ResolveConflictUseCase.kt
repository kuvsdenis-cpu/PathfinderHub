package com.pathfinder.hub.domain.usecase.offline
import com.pathfinder.hub.data.repository.SyncRepository
import javax.inject.Inject

class ResolveConflictUseCase @Inject constructor(private val syncRepo: SyncRepository) {
    suspend operator fun invoke(conflictId: String, by: String, resolution: String): Result<Unit> = try {
        syncRepo.resolveConflict(conflictId, by, System.currentTimeMillis(), resolution)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
