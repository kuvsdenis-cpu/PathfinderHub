package com.pathfinder.hub.data.repository
import com.pathfinder.hub.data.local.dao.SettingsDao
import com.pathfinder.hub.data.local.entity.settings.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsRepository @Inject constructor(private val dao: SettingsDao) {
    fun observeUserSettings(u: String): Flow<UserSettingsEntity?> = dao.observeUserSettings(u)
    suspend fun upsertUserSettings(s: UserSettingsEntity) = dao.upsertUserSettings(s)
    suspend fun resetUserSettings(u: String) = dao.resetUserSettings(u)
    fun observeClubSettings(c: String): Flow<ClubSettingsEntity?> = dao.observeClubSettings(c)
    suspend fun upsertClubSettings(s: ClubSettingsEntity) = dao.upsertClubSettings(s)
    fun observeConferenceSettings(id: String): Flow<ConferenceSettingsEntity?> = dao.observeConferenceSettings(id)
    suspend fun upsertConferenceSettings(s: ConferenceSettingsEntity) = dao.upsertConferenceSettings(s)
    fun observeSystemSettings(): Flow<SystemSettingsEntity?> = dao.observeSystemSettings()
    suspend fun upsertSystemSettings(s: SystemSettingsEntity) = dao.upsertSystemSettings(s)
}
