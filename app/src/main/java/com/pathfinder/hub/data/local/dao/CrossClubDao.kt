package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.crossclub.CrossClubInviteCodeEntity
import com.pathfinder.hub.data.local.entity.crossclub.CrossClubInviteEntity
import com.pathfinder.hub.data.local.entity.crossclub.CrossClubInviteUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CrossClubDao {

    // ---------- Явные приглашения ----------

    @Query("SELECT * FROM cross_club_invites WHERE hostClubId = :clubId ORDER BY createdAt DESC")
    fun observeByHost(clubId: String): Flow<List<CrossClubInviteEntity>>

    @Query("SELECT * FROM cross_club_invites WHERE inviteeClubId = :clubId ORDER BY createdAt DESC")
    fun observeByInvitee(clubId: String): Flow<List<CrossClubInviteEntity>>

    @Query("SELECT * FROM cross_club_invites WHERE id = :id LIMIT 1")
    suspend fun getInvite(id: String): CrossClubInviteEntity?

    @Query("SELECT COUNT(*) FROM cross_club_invites WHERE hostClubId = :clubId AND status = 'pending'")
    suspend fun countPendingByHost(clubId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvite(i: CrossClubInviteEntity)

    @Query("UPDATE cross_club_invites SET status = :status WHERE id = :id")
    suspend fun updateInviteStatus(id: String, status: String)

    // ---------- Коды teen → teen ----------

    @Query("SELECT * FROM cross_club_invite_codes WHERE createdBy = :userId ORDER BY createdAt DESC")
    fun observeCodesByCreator(userId: String): Flow<List<CrossClubInviteCodeEntity>>

    @Query("SELECT * FROM cross_club_invite_codes WHERE hostClubId = :clubId ORDER BY createdAt DESC")
    fun observeCodesByClub(clubId: String): Flow<List<CrossClubInviteCodeEntity>>

    @Query("SELECT * FROM cross_club_invite_codes WHERE code = :code LIMIT 1")
    suspend fun getCode(code: String): CrossClubInviteCodeEntity?

    @Query("SELECT * FROM cross_club_invite_codes WHERE id = :id LIMIT 1")
    suspend fun getCodeById(id: String): CrossClubInviteCodeEntity?

    @Query("SELECT COUNT(*) FROM cross_club_invite_codes WHERE createdBy = :userId AND createdAt >= :sinceMs")
    suspend fun countCodesCreatedSince(userId: String, sinceMs: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCode(c: CrossClubInviteCodeEntity)

    @Query("UPDATE cross_club_invite_codes SET usedCount = usedCount + 1 WHERE id = :id")
    suspend fun incrementCodeUse(id: String)

    @Query("UPDATE cross_club_invite_codes SET status = :status WHERE id = :id")
    suspend fun updateCodeStatus(id: String, status: String)

    // ---------- Использования ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsage(u: CrossClubInviteUsageEntity)

    @Query("SELECT * FROM cross_club_invite_usages WHERE inviteCodeId = :codeId ORDER BY usedAt DESC")
    fun observeUsagesByCode(codeId: String): Flow<List<CrossClubInviteUsageEntity>>

    @Query("SELECT COUNT(*) FROM cross_club_invite_usages WHERE inviteCodeId = :codeId AND userId = :userId")
    suspend fun countUsagesByUser(codeId: String, userId: String): Int

    @Query("SELECT * FROM cross_club_invite_usages WHERE userId = :userId ORDER BY usedAt DESC")
    fun observeUsagesByUser(userId: String): Flow<List<CrossClubInviteUsageEntity>>
}
