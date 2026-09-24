package com.pathfinder.hub.domain.usecase.learning
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.local.entity.learning.BookReportEntity
import java.util.UUID
import javax.inject.Inject

class SubmitBookReportUseCase @Inject constructor(private val honorRepo: HonorRepository) {
    suspend operator fun invoke(
        userId: String, bookId: String, type: String, content: String
    ): Result<String> = try {
        val id = UUID.randomUUID().toString()
        honorRepo.upsertBookReport(BookReportEntity(
            id = id, userId = userId, bookId = bookId, type = type,
            content = content, status = "submitted", approvedBy = null,
            approvedAt = null, pendingSync = true
        ))
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }
}
