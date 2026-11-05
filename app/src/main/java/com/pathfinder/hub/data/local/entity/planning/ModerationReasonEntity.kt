package com.pathfinder.hub.data.local.entity.planning

import androidx.room.*
import java.util.Date

/**
 * Справочник причин скрытия (модерации).
 * code — ключ, используется в CommentEntity.hiddenReasonCode / ModerationRequestEntity.hiddenReasonCode.
 * isSystem = true — нельзя удалить (можно деактивировать).
 */
@Entity(tableName = "moderation_reasons")
data class ModerationReasonEntity(
    @PrimaryKey val code: String,
    val title: String,
    val description: String,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val createdAt: Date = Date(),
)
