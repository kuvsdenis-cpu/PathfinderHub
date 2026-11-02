package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.parent.EventConsentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventConsentDao {

    @Query("SELECT * FROM event_consents WHERE parentId = :parentId ORDER BY requestedAt DESC")
    fun observeByParent(parentId: String): Flow<List<EventConsentEntity>>

    @Query("SELECT * FROM event_consents WHERE childId = :childId ORDER BY requestedAt DESC")
    fun observeByChild(childId: String): Flow<List<EventConsentEntity>>

    @Query("SELECT * FROM event_consents WHERE eventId = :eventId ORDER BY requestedAt DESC")
    fun observeByEvent(eventId: String): Flow<List<EventConsentEntity>>

    @Query("SELECT * FROM event_consents WHERE eventId = :eventId AND childId = :childId AND parentId = :parentId LIMIT 1")
    suspend fun getOne(eventId: String, childId: String, parentId: String): EventConsentEntity?

    @Query("SELECT COUNT(*) FROM event_consents WHERE eventId = :eventId AND status = 'pending'")
    suspend fun countPendingByEvent(eventId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: EventConsentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cs: List<EventConsentEntity>)

    @Query("""
        DELETE FROM event_consents
        WHERE eventId IN (
            SELECT id FROM events WHERE purgeAfter IS NOT NULL AND purgeAfter < :now
        )
    """)
    suspend fun deleteOrphansForExpiredEvents(now: Long): Int
}
