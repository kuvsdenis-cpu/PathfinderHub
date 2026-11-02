package com.pathfinder.hub.data.local.entity.groups

import androidx.room.Entity
import androidx.room.Index
import java.util.Date

/**
 * Исключение пользователя из кастомной группы (B-выход).
 *
 * Участники группы вычисляются динамически по CustomGroupRule,
 * поэтому «выйти» = добавить запись сюда. При resolveMembers
 * исключённые вычитаются из результата правила.
 */
@Entity(
    tableName = "custom_group_exclusions",
    primaryKeys = ["groupId", "userId"],
    indices = [Index("groupId"), Index("userId"), Index("pendingSync")]
)
data class CustomGroupExclusionEntity(
    val groupId: String,
    val userId: String,
    val excludedAt: Date,
    val excludedBy: String,        // кто исключил: сам userId (self-leave) или staff
    val reason: String = "left",   // "left" | "removed"
    val pendingSync: Boolean = false,
)
