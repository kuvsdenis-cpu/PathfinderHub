package com.pathfinder.hub.domain.usecase.learning
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.local.entity.learning.*
import java.util.Date
import javax.inject.Inject

class SubmitRequirementUseCase @Inject constructor(
    private val levelRepo: LevelRepository,
    private val honorRepo: HonorRepository
) {
    suspend fun level(u: String, l: String, r: String): Result<Unit> = try {
        levelRepo.upsertProgress(LevelProgressEntity(
            userId = u, levelId = l, requirementId = r, status = "submitted",
            submittedAt = Date(), approvedBy = null, approvedAt = null,
            comment = null, pendingSync = true
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun honor(u: String, h: String, r: String, media: List<String> = emptyList()): Result<Unit> = try {
        honorRepo.upsertProgress(HonorProgressEntity(
            userId = u, honorId = h, requirementId = r, status = "submitted",
            submittedAt = Date(), approvedBy = null, approvedAt = null,
            mediaUrls = media, comment = null, pendingSync = true
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
