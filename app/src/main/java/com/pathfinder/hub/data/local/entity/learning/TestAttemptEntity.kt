package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(
    tableName = "test_attempts",
    indices = [Index("userId"), Index("honorId"), Index("testId")]
)
data class TestAttemptEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val honorId: String = "",
    val testId: String? = null,
    val score: Int,
    val passed: Boolean,
    val attemptNumber: Int,
    val startedAt: Date,
    val completedAt: Date,
    val pendingSync: Boolean = false,
    /**
     * JSON-строка {questionId: answer} для choice-вопросов.
     * Нужна для пересчёта passed после проверки long_text.
     * Пустая мапа = "{}".
     */
    val choiceAnswers: String = "{}",
)
