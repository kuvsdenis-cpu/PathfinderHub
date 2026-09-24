package com.pathfinder.hub.domain.usecase.planning
import com.pathfinder.hub.data.repository.TaskRepository
import javax.inject.Inject

class ConfirmTaskUseCase @Inject constructor(private val taskRepo: TaskRepository) {
    suspend operator fun invoke(id: String, approve: Boolean): Result<Unit> = try {
        taskRepo.updateStatus(id, if (approve) "confirmed" else "rejected")
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
