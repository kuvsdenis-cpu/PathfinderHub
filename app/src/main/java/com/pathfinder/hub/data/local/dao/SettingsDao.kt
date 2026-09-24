package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.settings.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_settings WHERE userId = :userId")
    fun observeUserSettings(userId: String): Flow<UserSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserSettings(s: UserSettingsEntity)
    @Query("DELETE FROM user_settings WHERE userId = :userId")
    suspend fun resetUserSettings(userId: String)
    @Query("SELECT * FROM club_settings WHERE clubId = :clubId")
    fun observeClubSettings(clubId: String): Flow<ClubSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClubSettings(s: ClubSettingsEntity)
    @Query("SELECT * FROM conference_settings WHERE conferenceId = :id")
    fun observeConferenceSettings(id: String): Flow<ConferenceSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConferenceSettings(s: ConferenceSettingsEntity)
    @Query("SELECT * FROM system_settings WHERE id = 1")
    fun observeSystemSettings(): Flow<SystemSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSystemSettings(s: SystemSettingsEntity)
}
