package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.planning.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE id = :id")
    fun observeEvent(id: String): Flow<EventEntity?>
    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEvent(id: String): EventEntity?

    // ---------- Архивация ----------
    @Query("""
        SELECT * FROM events
        WHERE (clubId = :clubId OR clubId IS NULL)
          AND archivedAt IS NULL
          AND dateStart >= :from
          AND dateStart <= :to
        ORDER BY dateStart
    """)
    fun observeActiveInRange(clubId: String, from: Long, to: Long): Flow<List<EventEntity>>

    @Query("""
        SELECT * FROM events
        WHERE (clubId = :clubId OR clubId IS NULL)
          AND archivedAt IS NOT NULL
        ORDER BY archivedAt DESC
    """)
    fun observeArchivedByClub(clubId: String): Flow<List<EventEntity>>

    @Query("UPDATE events SET archivedAt = :at, purgeAfter = :purgeAfter WHERE id = :id")
    suspend fun archive(id: String, at: Long, purgeAfter: Long)

    @Query("UPDATE events SET archivedAt = NULL, purgeAfter = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM events WHERE purgeAfter IS NOT NULL AND purgeAfter < :now")
    suspend fun purgeExpired(now: Long): Int

    /**
     * Все опубликованные события клуба за период.
     * Отбираем по dateStart внутри [from, to].
     */
    @Query("""
        SELECT * FROM events
        WHERE clubId = :clubId
          AND status = 'published'
          AND dateStart >= :from
          AND dateStart <= :to
        ORDER BY dateStart ASC
    """)
    suspend fun getPublishedInPeriod(clubId: String, from: Long, to: Long): List<EventEntity>
    @Query("""SELECT * FROM events WHERE (clubId = :clubId OR clubId IS NULL)
        AND dateStart BETWEEN :from AND :to ORDER BY dateStart""")
    fun observeEventsInRange(clubId: String, from: Long, to: Long): Flow<List<EventEntity>>
    @Query("SELECT * FROM events WHERE status = :status ORDER BY dateStart")
    fun observeByStatus(status: String): Flow<List<EventEntity>>
    @Query("""SELECT * FROM events WHERE status = 'published'
        AND dateStart >= :now ORDER BY dateStart LIMIT :limit""")
    fun observeUpcoming(now: Long, limit: Int): Flow<List<EventEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<EventEntity>)
    @Update suspend fun update(event: EventEntity)
    @Query("UPDATE events SET status = :status, moderatedBy = :by, moderatedAt = :at WHERE id = :id")
    suspend fun updateModeration(id: String, status: String, by: String, at: Long)
    @Delete suspend fun delete(event: EventEntity)
    @Query("SELECT * FROM events WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<EventEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOccurrences(items: List<EventOccurrenceEntity>)
    @Query("SELECT * FROM event_occurrences WHERE eventId = :eventId ORDER BY dateStart")
    suspend fun getOccurrences(eventId: String): List<EventOccurrenceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEventHonors(items: List<EventHonorEntity>)
    @Query("SELECT * FROM event_honors WHERE eventId = :eventId")
    suspend fun getEventHonors(eventId: String): List<EventHonorEntity>

    // ---------- A2: групповые события ----------
    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN event_participants p
          ON p.eventId = e.id AND p.userId = :userId
        WHERE (e.clubId = :clubId OR e.clubId IS NULL)
          AND e.archivedAt IS NULL
          AND e.dateStart >= :from
          AND e.dateStart <= :to
          AND (e.targetGroup IS NULL OR p.userId IS NOT NULL)
        ORDER BY e.dateStart
    """)
    fun observeActiveInRangeForUser(
        clubId: String, userId: String, from: Long, to: Long
    ): Flow<List<EventEntity>>

    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN event_participants p
          ON p.eventId = e.id AND p.userId = :userId
        WHERE (e.clubId = :clubId OR e.clubId IS NULL)
          AND e.archivedAt IS NULL
          AND (e.targetGroup IS NULL OR p.userId IS NOT NULL)
        ORDER BY e.dateStart
    """)
    fun observeActiveByClubForUser(
        clubId: String, userId: String
    ): Flow<List<EventEntity>>
}
