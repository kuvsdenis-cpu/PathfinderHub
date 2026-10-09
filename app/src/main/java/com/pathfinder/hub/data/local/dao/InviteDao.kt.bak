package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InviteDao {
    @Query("SELECT * FROM invites WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): InviteEntity?
    @Query("SELECT * FROM invites WHERE clubId = :clubId")
    fun observeByClub(clubId: String): Flow<List<InviteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(invite: InviteEntity)
    @Query("UPDATE invites SET status = :status, usedBy = :userId, usedAt = :at WHERE code = :code")
    suspend fun markUsed(code: String, status: String, userId: String, at: Long)
    @Query("UPDATE invites SET status = 'expired' WHERE expiresAt < :now AND status = 'active'")
    suspend fun expireOld(now: Long)
}
