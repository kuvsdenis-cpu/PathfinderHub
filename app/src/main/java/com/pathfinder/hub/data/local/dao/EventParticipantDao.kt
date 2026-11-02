package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.planning.EventParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventParticipantDao {
    @Query("SELECT * FROM event_participants WHERE eventId = :eventId")
    fun observeParticipants(eventId: String): Flow<List<EventParticipantEntity>>

    @Query("SELECT * FROM event_participants WHERE userId = :userId")
    fun observeEventsForUser(userId: String): Flow<List<EventParticipantEntity>>

    @Query("SELECT * FROM event_participants WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    suspend fun getParticipant(eventId: String, userId: String): EventParticipantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EventParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EventParticipantEntity>)

    @Query("""
        UPDATE event_participants
        SET status = :status, respondedAt = :respondedAt, attendedAt = :attendedAt,
            pendingSync = 1
        WHERE eventId = :eventId AND userId = :userId
    """)
    suspend fun updateStatus(
        eventId: String,
        userId: String,
        status: String,
        respondedAt: Long?,
        attendedAt: Long?
    )

    @Query("SELECT * FROM event_participants WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<EventParticipantEntity>

    @Query("""
        UPDATE event_participants
        SET questions = :questions, pendingSync = 1
        WHERE eventId = :eventId AND userId = :userId
    """)
    suspend fun updateQuestions(eventId: String, userId: String, questions: List<String>)

    @Query("DELETE FROM event_participants WHERE eventId = :eventId")
    suspend fun deleteByEvent(eventId: String)

    @Query("DELETE FROM event_participants WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)

    @Query("""
        DELETE FROM event_participants
        WHERE eventId IN (
            SELECT id FROM events WHERE purgeAfter IS NOT NULL AND purgeAfter < :now
        )
    """)
    suspend fun deleteOrphansForExpiredEvents(now: Long): Int
}
