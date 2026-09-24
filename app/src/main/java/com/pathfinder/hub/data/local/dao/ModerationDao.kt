package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.planning.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ModerationDao {
    @Query("""SELECT * FROM comments WHERE targetType = :type AND targetId = :targetId
        AND status = 'published' ORDER BY createdAt""")
    fun observeApprovedComments(type: String, targetId: String): Flow<List<CommentEntity>>
    @Query("SELECT * FROM comments WHERE authorId = :userId ORDER BY createdAt DESC")
    fun observeUserComments(userId: String): Flow<List<CommentEntity>>
    @Query("SELECT * FROM comments WHERE status IN ('pending_review', 'draft')")
    fun observePendingComments(): Flow<List<CommentEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComment(c: CommentEntity)
    @Query("""UPDATE comments SET status = :status, moderatedBy = :by,
        moderatedAt = :at, hiddenReasonCode = :reason WHERE id = :id""")
    suspend fun moderateComment(id: String, status: String, by: String, at: Long, reason: String?)
    @Query("""SELECT * FROM moderation_requests WHERE clubId = :clubId
        AND status = 'pending' ORDER BY createdAt""")
    fun observePendingRequests(clubId: String): Flow<List<ModerationRequestEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequest(r: ModerationRequestEntity)
    @Query("""UPDATE moderation_requests SET status = :status, moderatedBy = :by,
        moderatedAt = :at, hiddenReasonCode = :reason WHERE id = :id""")
    suspend fun resolveRequest(id: String, status: String, by: String, at: Long, reason: String?)
    @Query("""UPDATE moderation_requests SET escalationLevel = :level,
        escalatedTo = :to, escalatedAt = :at WHERE id = :id""")
    suspend fun escalate(id: String, level: Int, to: String, at: Long)
    @Query("SELECT * FROM appeals WHERE status = 'pending'")
    fun observePendingAppeals(): Flow<List<AppealEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppeal(a: AppealEntity)
    @Query("""UPDATE appeals SET status = :status, reviewedBy = :by, reviewedAt = :at
        WHERE id = :id""")
    suspend fun resolveAppeal(id: String, status: String, by: String, at: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(h: ModerationHistoryEntity)
}
