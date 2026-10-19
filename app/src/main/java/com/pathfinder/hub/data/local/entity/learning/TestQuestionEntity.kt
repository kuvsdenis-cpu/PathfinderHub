package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*

@Entity(
    tableName = "test_questions",
    indices = [Index("testId"), Index("clubId"), Index("honorId")]
)
data class TestQuestionEntity(
    @PrimaryKey val id: String,
    val testId: String? = null,
    val clubId: String? = null,
    val honorId: String = "",
    val type: String,
    val text: String,
    val options: List<String>,
    val correctAnswer: String,
    val points: Int,
    val order: Int = 0,
)
