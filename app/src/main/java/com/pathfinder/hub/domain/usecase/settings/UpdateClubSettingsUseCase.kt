package com.pathfinder.hub.domain.usecase.settings
import com.pathfinder.hub.data.repository.SettingsRepository
import com.pathfinder.hub.data.local.entity.settings.ClubSettingsEntity
import javax.inject.Inject

class UpdateClubSettingsUseCase @Inject constructor(private val repo: SettingsRepository) {
    suspend operator fun invoke(s: ClubSettingsEntity): Result<Unit> = try {
        repo.upsertClubSettings(s)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
