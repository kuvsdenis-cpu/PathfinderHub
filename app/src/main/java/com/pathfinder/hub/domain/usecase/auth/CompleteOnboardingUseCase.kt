package com.pathfinder.hub.domain.usecase.auth
import com.pathfinder.hub.data.repository.UserRepository
import javax.inject.Inject

class CompleteOnboardingUseCase @Inject constructor(private val userRepo: UserRepository) {
    suspend operator fun invoke(userId: String): Result<Unit> = try {
        userRepo.completeOnboarding(userId)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
