package com.pathfinder.hub.data.local.entity.learning

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Сопутствующий материал требования ступени/специализации.
 *
 * Источник:
 *   - JSON (levels.json / honors.json) -> clubId = "", createdBy = null
 *   - Правка директора (Firestore)     -> clubId = <clubId>, createdBy = <uid>
 *
 * Флаг flaggedForEdit ставит наставник клуба: "материал надо поправить".
 * Редактирует только директор.
 */
@Entity(
    tableName = "requirement_materials",
    indices = [
        Index("requirementId"),
        Index("clubId"),
        Index("flaggedForEdit"),
        Index("requirementId", "clubId")
    ]
)
data class RequirementMaterialEntity(
    @PrimaryKey val id: String,
    val requirementId: String,
    val clubId: String,
    val type: String,
    val title: String,
    val content: String,
    val orderIndex: Int,
    val flaggedForEdit: Boolean = false,
    val flaggedBy: String? = null,
    val flaggedAt: Date? = null,
    val flagComment: String? = null,
    val createdBy: String? = null,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false
)
