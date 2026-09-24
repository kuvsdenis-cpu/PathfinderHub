package com.pathfinder.hub.domain.usecase.planning
import com.pathfinder.hub.data.repository.TaskRepository
import javax.inject.Inject

class StartTaskUseCase @Inject constructor(private val taskRepo: TaskRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = try {
        taskRepo.updateStatus(id, "in_progress")
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
