package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.core.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    fun observeUser(userId: String): Flow<UserEntity?>
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUser(userId: String): UserEntity?
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?
    @Query("SELECT * FROM users WHERE clubId = :clubId")
    fun observeClubMembers(clubId: String): Flow<List<UserEntity>>
    @Query("SELECT * FROM users WHERE clubId = :clubId AND role = :role")
    fun observeClubMembersByRole(clubId: String, role: String): Flow<List<UserEntity>>
    @Query("SELECT * FROM users WHERE conferenceId = :conferenceId")
    fun observeConferenceUsers(conferenceId: String): Flow<List<UserEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(users: List<UserEntity>)
    @Update suspend fun update(user: UserEntity)
    @Delete suspend fun delete(user: UserEntity)
    @Query("UPDATE users SET accessLevel = :level WHERE id = :userId")
    suspend fun updateAccessLevel(userId: String, level: String)
    @Query("UPDATE users SET onboardingCompleted = 1 WHERE id = :userId")
    suspend fun completeOnboarding(userId: String)
    @Query("UPDATE users SET status = :status, deletionRequestedAt = :at WHERE id = :userId")
    suspend fun requestDeletion(userId: String, status: String, at: Long)
    @Query("DELETE FROM users WHERE status = 'deleted' AND permanentDeletionAt < :now")
    suspend fun purgeDeleted(now: Long)
}
