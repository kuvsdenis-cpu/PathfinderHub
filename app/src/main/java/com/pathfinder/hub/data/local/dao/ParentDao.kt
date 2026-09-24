package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.parent.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ParentDao {
    @Query("SELECT * FROM parent_child_links WHERE parentId = :parentId")
    fun observeChildren(parentId: String): Flow<List<ParentChildLinkEntity>>
    @Query("SELECT * FROM parent_child_links WHERE childId = :childId")
    fun observeParents(childId: String): Flow<List<ParentChildLinkEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLink(l: ParentChildLinkEntity)
    @Query("""UPDATE parent_child_links SET status = 'revoked',
        revokedAt = :at, revokedReason = :reason
        WHERE parentId = :p AND childId = :c""")
    suspend fun revokeLink(p: String, c: String, at: Long, reason: String?)
    @Query("SELECT * FROM payments WHERE childId = :childId ORDER BY dueDate DESC")
    fun observeChildPayments(childId: String): Flow<List<PaymentEntity>>
    @Query("SELECT * FROM payments WHERE parentId = :parentId ORDER BY dueDate DESC")
    fun observeParentPayments(parentId: String): Flow<List<PaymentEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(p: PaymentEntity)
    @Query("""UPDATE payments SET status = :status, confirmedBy = :by, confirmedAt = :at
        WHERE id = :id""")
    suspend fun confirmPayment(id: String, status: String, by: String, at: Long)
    @Query("SELECT * FROM parent_messages WHERE parentId = :parentId ORDER BY createdAt DESC")
    fun observeMessages(parentId: String): Flow<List<ParentMessageEntity>>
    @Query("SELECT * FROM parent_messages WHERE status = 'pending'")
    fun observePendingMessages(): Flow<List<ParentMessageEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(m: ParentMessageEntity)
    @Query("""UPDATE parent_messages SET status = :status, moderatedBy = :by,
        moderatedAt = :at WHERE id = :id""")
    suspend fun moderateMessage(id: String, status: String, by: String, at: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeeting(m: ParentMeetingEntity)
    @Query("SELECT * FROM pwa_config WHERE id = 1")
    fun observePWAConfig(): Flow<PWAConfigEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPWAConfig(c: PWAConfigEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun logPWAInstall(e: PWAInstallEventEntity)
}
