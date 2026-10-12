package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.planning.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ModerationDao {
    @Query("""SELECT * FROM comments WHERE targetType = :type AND targetId = :targetId
        AND status = 'published' AND archivedAt IS NULL ORDER BY createdAt""")
    fun observeApprovedComments(type: String, targetId: String): Flow<List<CommentEntity>>

    // ============ A3.1: Архивация ============

    @Query("""SELECT * FROM comments WHERE targetType = :type AND targetId = :targetId
        AND status = 'published' AND archivedAt IS NOT NULL ORDER BY archivedAt DESC""")
    fun observeArchivedComments(type: String, targetId: String): Flow<List<CommentEntity>>

    @Query("""SELECT * FROM comments WHERE clubId = :clubId AND archivedAt IS NOT NULL
        ORDER BY archivedAt DESC""")
    fun observeArchivedCommentsByClub(clubId: String): Flow<List<CommentEntity>>

    @Query("UPDATE comments SET archivedAt = :at, purgeAfter = :purgeAfter WHERE id = :id")
    suspend fun archiveComment(id: String, at: Long, purgeAfter: Long)

    @Query("UPDATE comments SET archivedAt = NULL, purgeAfter = NULL WHERE id = :id")
    suspend fun restoreComment(id: String)

    /**
     * Авто-архивация: все опубликованные комментарии старше threshold.
     * purgeAfter = сейчас + 90 дней.
     */
    @Query("""UPDATE comments
        SET archivedAt = :now, purgeAfter = :purgeAfter
        WHERE archivedAt IS NULL
          AND status = 'published'
          AND createdAt < :threshold""")
    suspend fun autoArchiveOld(threshold: Long, now: Long, purgeAfter: Long): Int

    @Query("DELETE FROM comments WHERE purgeAfter IS NOT NULL AND purgeAfter < :now")
    suspend fun purgeExpiredComments(now: Long): Int
    @Query("SELECT * FROM comments WHERE authorId = :userId ORDER BY createdAt DESC")
    fun observeUserComments(userId: String): Flow<List<CommentEntity>>
    @Query("SELECT * FROM comments WHERE status IN ('pending_review', 'draft')")
    fun observePendingComments(): Flow<List<CommentEntity>>
    @Query("SELECT COUNT(*) FROM comments WHERE authorId = :userId")
    suspend fun countUserComments(userId: String): Int

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
    @Query("""UPDATE comments SET status = 'hidden', 
              hiddenReasonCode = 'review_timeout',
              hiddenReasonNote = 'Истёк срок ручной модерации'
              WHERE status = 'published' 
              AND reviewDeadline IS NOT NULL 
              AND reviewDeadline < :now
              AND moderatedBy IS NULL""")
    suspend fun hideExpiredComments(now: Long): Int
}
