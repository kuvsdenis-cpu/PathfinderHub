package com.pathfinder.hub.domain.usecase.offline
import com.pathfinder.hub.data.repository.SyncRepository
import com.pathfinder.hub.data.repository.NewspaperRepository
import javax.inject.Inject

class ClearCacheUseCase @Inject constructor(
    private val syncRepo: SyncRepository,
    private val newspaperRepo: NewspaperRepository
) {
    suspend operator fun invoke(): Result<Long> = try {
        var freed = 0L
        val cached = newspaperRepo.getOldestCached(10)
        for (c in cached) {
            freed += c.fileSizeBytes
            newspaperRepo.deleteCached(c.issueId)
            syncRepo.deleteCache(c.issueId)
        }
        Result.success(freed)
    } catch (e: Exception) { Result.failure(e) }
}
