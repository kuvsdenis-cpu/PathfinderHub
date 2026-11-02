package com.pathfinder.hub.data.local.entity.sabbath

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sabbath_answers")
data class SabbathAnswerEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val lessonId: String,
    val blockId: String,
    val answerText: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = false
)
