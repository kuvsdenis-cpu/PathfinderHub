package com.pathfinder.hub.domain.usecase.learning
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.local.entity.learning.HonorDraftEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class CreateHonorDraftUseCase @Inject constructor(private val honorRepo: HonorRepository) {
    suspend operator fun invoke(
        authorId: String, clubId: String, name: String, category: String,
        level: Int, description: String, requirements: List<String>, questions: List<String>
    ): Result<String> = try {
        val id = UUID.randomUUID().toString()
        honorRepo.upsertDraft(HonorDraftEntity(
            id = id, authorId = authorId, clubId = clubId, name = name,
            category = category, level = level, description = description,
            requirements = requirements, questions = questions, status = "draft",
            reviewedBy = null, reviewedAt = null, rejectionReason = null,
            createdAt = Date()
        ))
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }
}
