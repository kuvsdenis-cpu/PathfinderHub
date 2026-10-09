package com.pathfinder.hub.data.local.entity.learning

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Проверочное задание требования.
 *
 * Типы:
 *   Автопроверка (autoCheck = true):
 *     "single_choice" | "multi_choice" | "number" | "cipher" | "crossword"
 *   Ручная проверка (autoCheck = false):
 *     "short_text" | "long_text" | "file" | "date" | "checkbox"
 *
 * Флаг flaggedForEdit ставит наставник клуба. Редактирует только директор.
 */
@Entity(
    tableName = "requirement_tasks",
    indices = [
        Index("requirementId"),
        Index("clubId"),
        Index("flaggedForEdit"),
        Index("requirementId", "clubId")
    ]
)
data class RequirementTaskEntity(
    @PrimaryKey val id: String,
    val requirementId: String,
    val clubId: String,
    val type: String,
    val title: String,
    val description: String? = null,
    val options: List<String> = emptyList(),
    val correctAnswer: String? = null,
    val autoCheck: Boolean = false,
    val isRequired: Boolean = true,
    val orderIndex: Int,
    val flaggedForEdit: Boolean = false,
    val flaggedBy: String? = null,
    val flaggedAt: Date? = null,
    val flagComment: String? = null,
    val createdBy: String? = null,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false
) {
    companion object {
        private val AUTO_CHECK_TYPES = setOf(
            "single_choice", "multi_choice", "number", "cipher", "crossword"
        )

        fun isAutoCheckType(type: String): Boolean = type in AUTO_CHECK_TYPES
    }
}
