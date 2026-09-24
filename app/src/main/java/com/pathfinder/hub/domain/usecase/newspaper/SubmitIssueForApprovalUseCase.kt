package com.pathfinder.hub.domain.usecase.newspaper
import com.pathfinder.hub.data.repository.NewspaperRepository
import com.pathfinder.hub.data.local.entity.newspaper.NewspaperModerationEntity
import java.util.Date
import javax.inject.Inject

class SubmitIssueForApprovalUseCase @Inject constructor(private val repo: NewspaperRepository) {
    suspend operator fun invoke(issueId: String, requestedBy: String): Result<Unit> = try {
        repo.upsertModeration(NewspaperModerationEntity(
            issueId = issueId, requestedBy = requestedBy, requestedAt = Date(),
            reviewedBy = null, reviewedAt = null, status = "pending", rejectionReason = null
        ))
        repo.updateStatus(issueId, "pending")
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
