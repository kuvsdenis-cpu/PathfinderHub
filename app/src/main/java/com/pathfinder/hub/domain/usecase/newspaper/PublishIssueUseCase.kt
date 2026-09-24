package com.pathfinder.hub.domain.usecase.newspaper
import com.pathfinder.hub.data.repository.NewspaperRepository
import com.pathfinder.hub.data.local.entity.newspaper.NewspaperPublicationEntity
import java.util.Date
import javax.inject.Inject

class PublishIssueUseCase @Inject constructor(private val repo: NewspaperRepository) {
    suspend operator fun invoke(
        issueId: String, pdfUrl: String, pageCount: Int, sizeBytes: Int
    ): Result<Unit> = try {
        repo.upsertPublication(NewspaperPublicationEntity(
            issueId = issueId, pdfUrl = pdfUrl, pageCount = pageCount,
            fileSizeBytes = sizeBytes, generatedAt = Date(), notifiedAt = null
        ))
        repo.updateStatus(issueId, "published")
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
