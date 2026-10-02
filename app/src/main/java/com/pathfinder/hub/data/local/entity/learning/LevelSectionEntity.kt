package com.pathfinder.hub.data.local.entity.learning

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Секция ступени. id секции уникален В РАМКАХ уровня (составной PK: levelId + id).
 * Это позволяет иметь "general" в каждом уровне, не конфликтуя по PK.
 */
@Entity(
    tableName = "level_sections",
    primaryKeys = ["levelId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LevelEntity::class,
            parentColumns = ["id"],
            childColumns = ["levelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("levelId")]
)
data class LevelSectionEntity(
    val id: String,
    val levelId: String,
    val name: String,
    val order: Int
)