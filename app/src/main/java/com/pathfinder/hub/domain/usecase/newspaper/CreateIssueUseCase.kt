package com.pathfinder.hub.domain.usecase.newspaper
import com.pathfinder.hub.data.repository.NewspaperRepository
import com.pathfinder.hub.data.local.entity.newspaper.NewspaperIssueEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class CreateIssueUseCase @Inject constructor(private val repo: NewspaperRepository) {
    suspend operator fun invoke(i: NewspaperIssueEntity): Result<String> = try {
        val id = i.id.ifBlank { UUID.randomUUID().toString() }
        repo.upsert(i.copy(id = id, status = "draft", createdAt = Date()))
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }
}
