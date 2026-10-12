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
    /**
     * Участники клуба. Фильтр через user_clubs (мультиклубность),
     * а не через users.clubId — чтобы юзеры с этим клубом во вторичном
     * clubIds тоже попадали.
     */
    @Transaction
    @Query("""
        SELECT u.* FROM users u
        WHERE EXISTS (
            SELECT 1 FROM user_clubs uc
            WHERE uc.userId = u.id AND uc.clubId = :clubId
        )
        ORDER BY u.role, u.firstName
    """)
    fun observeClubMembers(clubId: String): Flow<List<UserEntity>>
    @Transaction
    @Query("""
        SELECT u.* FROM users u
        WHERE EXISTS (
            SELECT 1 FROM user_clubs uc
            WHERE uc.userId = u.id AND uc.clubId = :clubId
        )
        AND u.role = :role
        ORDER BY u.role, u.firstName
    """)
    fun observeClubMembersByRole(clubId: String, role: String): Flow<List<UserEntity>>

    /**
     * Список участников клуба с одной из указанных ролей.
     * Используется для «Сотрудников»: director + instructor + secretary.
     * Фильтр через user_clubs (мультиклубность).
     */
    @Transaction
    @Query("""
        SELECT u.* FROM users u
        WHERE EXISTS (
            SELECT 1 FROM user_clubs uc
            WHERE uc.userId = u.id AND uc.clubId = :clubId
        )
        AND u.role IN (:roles)
        ORDER BY u.role, u.firstName
    """)
    fun observeClubMembersByRoles(clubId: String, roles: List<String>): Flow<List<UserEntity>>
    @Query("SELECT * FROM users WHERE conferenceId = :conferenceId")
    fun observeConferenceUsers(conferenceId: String): Flow<List<UserEntity>>
    @Query("SELECT * FROM users WHERE conferenceId = :conferenceId AND role = :role")
    fun observeConferenceMembersByRole(conferenceId: String, role: String): Flow<List<UserEntity>>
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

    /** Для UserClubSeeder: все юзеры с непустым clubId или clubIds. */
    @Query("SELECT * FROM users WHERE clubId IS NOT NULL OR clubIds != '[]'")
    suspend fun getAllWithClubIds(): List<UserEntity>

    /** Пакетный резолв имён: один запрос вместо N. */
    @Query("SELECT * FROM users WHERE id IN (:ids)")
    suspend fun getUsersByIds(ids: List<String>): List<UserEntity>
}
