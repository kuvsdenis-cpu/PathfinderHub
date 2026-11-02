package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.conference.ConferenceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConferenceEventDao {

    @Query("SELECT * FROM conference_events WHERE conferenceId = :confId AND status = 'published' ORDER BY dateStart ASC")
    fun observePublishedByConference(confId: String): Flow<List<ConferenceEventEntity>>

    @Query("SELECT * FROM conference_events WHERE conferenceId = :confId ORDER BY dateStart DESC")
    fun observeAllByConference(confId: String): Flow<List<ConferenceEventEntity>>

    @Query("SELECT * FROM conference_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConferenceEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: ConferenceEventEntity)

    @Query("DELETE FROM conference_events WHERE id = :id")
    suspend fun deleteById(id: String)
}
