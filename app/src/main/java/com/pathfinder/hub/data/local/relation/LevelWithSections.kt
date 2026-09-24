package com.pathfinder.hub.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity

/**
 * Relation: уровень + его разделы.
 *
 * Room не поддерживает вложенные @Relation с data-классами-обёртками,
 * поэтому связь "уровень → разделы" сделана в один уровень.
 *
 * Требования разделов (LevelRequirementEntity) запрашиваются отдельным
 * DAO-методом: LevelDao.observeRequirements(levelId).
 */
data class LevelWithSections(
    @Embedded val level: LevelEntity,
    @Relation(
        entity = LevelSectionEntity::class,
        parentColumn = "id",
        entityColumn = "levelId"
    )
    val sections: List<LevelSectionEntity>
)