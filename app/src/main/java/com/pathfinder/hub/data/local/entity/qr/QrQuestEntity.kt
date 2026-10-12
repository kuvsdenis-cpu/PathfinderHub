package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * QR-квест — серия станций, которые teen проходит по порядку.
 *
 * Тип: только "linear" (строго по порядку) на данной итерации.
 *
 * dateStart/dateEnd nullable:
 *   - dateEnd = null  → бессрочный
 *   - dateEnd != null → ограничен по дате
 */
@Entity(
    tableName = "qr_quests",
    indices = [Index("clubId"), Index("status")]
)
data class QrQuestEntity(
    @PrimaryKey val id: String,
    val clubId: String,
    val title: String,
    val description: String = "",
    val type: String = "linear",
    val rewardPoints: Int = 0,
    val rewardAchievementId: String? = null,
    val dateStart: Date? = null,
    val dateEnd: Date? = null,
    val status: String = "active",   // "active" | "archived"
    val createdBy: String,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false,

    // A3.2: Архивация
    val archivedAt: Date? = null,
    val purgeAfter: Date? = null,
)
