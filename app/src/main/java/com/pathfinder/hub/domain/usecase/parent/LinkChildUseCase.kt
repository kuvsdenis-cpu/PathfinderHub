package com.pathfinder.hub.domain.usecase.parent
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import java.util.Date
import javax.inject.Inject

class LinkChildUseCase @Inject constructor(private val repo: ParentRepository) {
    suspend operator fun invoke(
        parentId: String, childId: String, clubId: String,
        linkedBy: String, relationship: String = "parent"
    ): Result<Unit> = try {
        repo.upsertLink(ParentChildLinkEntity(
            parentId = parentId, childId = childId, clubId = clubId,
            relationship = relationship, status = "active", linkedBy = linkedBy,
            linkedAt = Date(), revokedAt = null, revokedReason = null
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
