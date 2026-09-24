package com.pathfinder.hub.domain.usecase.parent
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.local.entity.parent.ParentMessageEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class SendParentMessageUseCase @Inject constructor(private val repo: ParentRepository) {
    suspend operator fun invoke(
        parentId: String, childId: String, clubId: String, directorId: String,
        subject: String, message: String
    ): Result<String> = try {
        val id = UUID.randomUUID().toString()
        repo.upsertMessage(ParentMessageEntity(
            id = id, parentId = parentId, childId = childId, clubId = clubId,
            directorId = directorId, subject = subject, message = message,
            status = "pending", moderatedBy = null, moderatedAt = null,
            respondedAt = null, response = null, createdAt = Date(), pendingSync = true
        ))
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }
}
