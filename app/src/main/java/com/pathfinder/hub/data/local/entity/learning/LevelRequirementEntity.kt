package com.pathfinder.hub.data.local.entity.learning

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Требование ступени.
 * Внешний ключ ссылается на составной PK LevelSectionEntity (levelId + sectionId).
 */
@Entity(
    tableName = "level_requirements",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = LevelSectionEntity::class,
            parentColumns = ["levelId", "id"],
            childColumns = ["levelId", "sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("levelId"), Index("sectionId"), Index("levelId", "sectionId")]
)
data class LevelRequirementEntity(
    val id: String,
    val sectionId: String,
    val levelId: String,
    val text: String,
    val type: String,
    val order: Int,
    val isAdvanced: Boolean,

    /**
     * Список URL изображений (pre-signed или локальные asset-пути).
     * Хранится в SQLite как JSON-строка через Converters.
     */
    val imageUrls: List<String> = emptyList()
)