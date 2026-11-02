package com.pathfinder.hub.data.local.entity.groups

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * B1: пользовательская группа с динамическим правилом.
 *
 * ruleJson — сериализованный [CustomGroupRule] (Gson).
 * Сериализация/десериализация — в CustomGroupRepository, не через Converters.
 *
 * Группа переиспользуется везде, где сейчас targetGroup:
 * задачи, события, встречи, взносы, чаты, рассылки.
 */
@Entity(
    tableName = "custom_groups",
    indices = [Index("clubId"), Index("createdBy"), Index("archivedAt")]
)
data class CustomGroupEntity(
    @PrimaryKey val id: String,
    val clubId: String,
    val name: String,
    val description: String = "",
    val createdBy: String,
    val createdAt: Date,
    val updatedAt: Date,
    val ruleJson: String,

    /**
     * Принудительные участники группы (JSON-массив userId).
     * Заполняется при создании: owner клуба + создатель.
     * resolveMembers добавляет их поверх правила.
     */
    val forcedMemberIdsJson: String = "[]",

    // B2: архивация (заложено сразу, чтобы не мигрировать дважды)
    val archivedAt: Date? = null,
    val purgeAfter: Date? = null,

    val pendingSync: Boolean = false,
)
