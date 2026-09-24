package com.pathfinder.hub.domain.usecase.settings
import com.pathfinder.hub.data.repository.SettingsRepository
import com.pathfinder.hub.data.local.entity.settings.UserSettingsEntity
import javax.inject.Inject

class UpdateUserSettingsUseCase @Inject constructor(private val repo: SettingsRepository) {
    suspend operator fun invoke(s: UserSettingsEntity): Result<Unit> = try {
        repo.upsertUserSettings(s)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
