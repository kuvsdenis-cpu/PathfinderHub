package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*

@Entity(tableName = "test_questions", indices = [Index("honorId")])
data class TestQuestionEntity(
    @PrimaryKey val id: String, val honorId: String, val type: String,
    val text: String, val options: List<String>, val correctAnswer: String, val points: Int
)
