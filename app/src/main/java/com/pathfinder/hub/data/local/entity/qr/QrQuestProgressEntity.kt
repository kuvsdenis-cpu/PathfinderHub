package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Прогресс teen по квесту.
 *
 * lastCompletedStepIndex — сколько шагов пройдено по порядку (N).
 * completedStepIndices — все отмеченные шаги (для истории).
 * Если lastCompletedStepIndex == всего шагов → completedAt != null.
 */
@Entity(
    tableName = "qr_quest_progress",
    primaryKeys = ["questId", "userId"],
    indices = [Index("questId"), Index("userId"), Index("clubId")]
)
data class QrQuestProgressEntity(
    val questId: String,
    val userId: String,
    val clubId: String,
    val lastCompletedStepIndex: Int = 0,
    val completedStepIndices: List<Int> = emptyList(),
    val startedAt: Date? = null,
    val completedAt: Date? = null,
    val rewardClaimed: Boolean = false,
    val pendingSync: Boolean = false,
)
