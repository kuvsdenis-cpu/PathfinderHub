package com.pathfinder.hub.domain.usecase.settings
import com.pathfinder.hub.data.repository.SettingsRepository
import javax.inject.Inject

class ResetUserSettingsUseCase @Inject constructor(private val repo: SettingsRepository) {
    suspend operator fun invoke(userId: String): Result<Unit> = try {
        repo.resetUserSettings(userId)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
