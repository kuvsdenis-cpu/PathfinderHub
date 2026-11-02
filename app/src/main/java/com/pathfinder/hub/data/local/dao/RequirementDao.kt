package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity

/**
 * DAO для level_requirements — только чтение (задача E).
 *
 * Используется в SyncWorker.pullRequirements, чтобы получить список
 * requirementId из ЛОКАЛЬНОЙ Room (сидовые данные), а не делать
 * list clubs/{clubId}/requirements (который падает с PERMISSION_DENIED —
 * в rules нет allow read на /requirements/{reqId}, только на подколлекции).
 *
 * Миграция НЕ нужна: level_requirements уже существует (v56+).
 */
@Dao
interface RequirementDao {

    /** Все requirementId (глобальные, сидовые). */
    @Query("SELECT id FROM level_requirements")
    suspend fun getAllRequirementIds(): List<String>

    /** Требования конкретной ступени. */
    @Query("SELECT id FROM level_requirements WHERE levelId = :levelId")
    suspend fun getRequirementIdsByLevel(levelId: String): List<String>

    /** Полные записи — на случай, если понадобится. */
    @Query("SELECT * FROM level_requirements")
    suspend fun getAllRequirements(): List<LevelRequirementEntity>
}
