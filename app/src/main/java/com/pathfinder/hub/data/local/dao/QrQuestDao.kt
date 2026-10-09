package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestProgressEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QrQuestDao {

    // -------- Квесты --------

    @Query("SELECT * FROM qr_quests WHERE clubId = :clubId AND status = 'active' ORDER BY createdAt DESC")
    fun observeActiveByClub(clubId: String): Flow<List<QrQuestEntity>>

    @Query("SELECT * FROM qr_quests WHERE clubId = :clubId ORDER BY createdAt DESC")
    fun observeAllByClub(clubId: String): Flow<List<QrQuestEntity>>

    @Query("SELECT * FROM qr_quests WHERE id = :id")
    suspend fun getById(id: String): QrQuestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuest(q: QrQuestEntity)

    @Query("DELETE FROM qr_quests WHERE id = :id")
    suspend fun deleteQuest(id: String)

    // -------- Связи квест-станция --------

    @Query("SELECT * FROM qr_quest_stations WHERE questId = :questId ORDER BY stepIndex")
    fun observeQuestStations(questId: String): Flow<List<QrQuestStationEntity>>

    @Query("SELECT * FROM qr_quest_stations WHERE questId = :questId ORDER BY stepIndex")
    suspend fun getQuestStations(questId: String): List<QrQuestStationEntity>

    /**
     * Найти все квесты, в которые входит станция.
     * Нужно при сканировании: одна станция = может быть в нескольких квестах.
     */
    @Query("SELECT * FROM qr_quest_stations WHERE stationId = :stationId ORDER BY stepIndex")
    suspend fun getQuestsForStation(stationId: String): List<QrQuestStationEntity>

    @Query("SELECT COUNT(*) FROM qr_quest_stations WHERE questId = :questId")
    suspend fun getStepCount(questId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestStation(s: QrQuestStationEntity)

    @Query("DELETE FROM qr_quest_stations WHERE questId = :questId")
    suspend fun clearQuestStations(questId: String)

    @Query("DELETE FROM qr_quest_stations WHERE questId = :questId AND stationId = :stationId")
    suspend fun deleteQuestStation(questId: String, stationId: String)

    // -------- Прогресс --------

    @Query("SELECT * FROM qr_quest_progress WHERE questId = :questId AND userId = :userId LIMIT 1")
    fun observeProgress(questId: String, userId: String): Flow<QrQuestProgressEntity?>

    @Query("SELECT * FROM qr_quest_progress WHERE questId = :questId AND userId = :userId LIMIT 1")
    suspend fun getProgress(questId: String, userId: String): QrQuestProgressEntity?

    @Query("SELECT * FROM qr_quest_progress WHERE userId = :userId")
    fun observeAllProgressByUser(userId: String): Flow<List<QrQuestProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: QrQuestProgressEntity)
}
