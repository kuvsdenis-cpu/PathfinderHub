package com.pathfinder.hub.domain.usecase.offline
import com.pathfinder.hub.data.repository.SyncRepository
import com.pathfinder.hub.data.local.entity.offline.SyncSettingsEntity
import javax.inject.Inject

class UpdateSyncSettingsUseCase @Inject constructor(private val syncRepo: SyncRepository) {
    suspend operator fun invoke(s: SyncSettingsEntity): Result<Unit> = try {
        syncRepo.upsertSettings(s)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
