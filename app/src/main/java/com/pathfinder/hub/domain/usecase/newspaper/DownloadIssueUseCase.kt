package com.pathfinder.hub.domain.usecase.newspaper
import com.pathfinder.hub.data.repository.NewspaperRepository
import com.pathfinder.hub.data.local.entity.newspaper.CachedNewspaperEntity
import java.util.Date
import javax.inject.Inject

class DownloadIssueUseCase @Inject constructor(private val repo: NewspaperRepository) {
    suspend operator fun invoke(issueId: String, localPath: String, size: Int): Result<Unit> = try {
        repo.upsertCached(CachedNewspaperEntity(
            issueId = issueId, localPath = localPath,
            downloadedAt = Date(), fileSizeBytes = size
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
