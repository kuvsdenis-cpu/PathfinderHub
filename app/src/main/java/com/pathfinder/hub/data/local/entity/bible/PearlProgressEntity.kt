package com.pathfinder.hub.data.local.entity.bible

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pearl_progress",
    indices = [
        Index(value = ["userId", "pearlId"], unique = true),
        Index("userId"),
    ]
)
data class PearlProgressEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val pearlId: String,
    val memorized: Boolean = false,
    val memorizedAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val reviewCount: Int = 0,
    // === B8: интерактивная тренировка ===
    val trainingScore: Int? = null,        // лучший результат, 0..100
    val trainingAttempts: Int = 0,         // всего попыток
    val lastTrainedAt: Long? = null,       // epoch millis последней тренировки
    val pendingSync: Boolean = true,
)
