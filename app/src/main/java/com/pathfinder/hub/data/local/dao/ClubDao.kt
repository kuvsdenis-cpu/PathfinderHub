package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.local.entity.core.ConferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClubDao {
    @Query("SELECT * FROM clubs WHERE id = :clubId")
    fun observeClub(clubId: String): Flow<ClubEntity?>
    @Query("SELECT * FROM clubs WHERE id = :clubId")
    suspend fun getClub(clubId: String): ClubEntity?

    @Query("SELECT * FROM clubs WHERE id IN (:ids)")
    suspend fun getClubs(ids: List<String>): List<ClubEntity>
    @Query("SELECT * FROM clubs WHERE conferenceId = :conferenceId AND deletedAt IS NULL ORDER BY name")
    fun observeClubsByConference(conferenceId: String): Flow<List<ClubEntity>>
    @Query("SELECT * FROM clubs WHERE deletedAt IS NULL ORDER BY name")
    fun observeAllClubs(): Flow<List<ClubEntity>>

    @Query("SELECT * FROM clubs WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeletedClubs(): Flow<List<ClubEntity>>

    @Query("SELECT * FROM clubs WHERE purgeAt IS NOT NULL AND purgeAt < :now")
    suspend fun getExpiredClubs(now: Long): List<ClubEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(club: ClubEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(clubs: List<ClubEntity>)
    @Update suspend fun update(club: ClubEntity)
    @Delete suspend fun delete(club: ClubEntity)
    @Query("SELECT * FROM conferences WHERE id = :id")
    suspend fun getConference(id: String): ConferenceEntity?
    @Query("SELECT * FROM conferences LIMIT 1")
    fun observeConference(): Flow<ConferenceEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConference(conference: ConferenceEntity)
}
