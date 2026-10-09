package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Связь квест ↔ станция с порядковым номером.
 *
 * Составной PK: одна станция может быть в нескольких квестах,
 * в каждом — со своим stepIndex.
 */
@Entity(
    tableName = "qr_quest_stations",
    primaryKeys = ["questId", "stationId"],
    indices = [Index("questId"), Index("stationId")]
)
data class QrQuestStationEntity(
    val questId: String,
    val stationId: String,
    val stepIndex: Int,   // 1, 2, 3, ...
)
