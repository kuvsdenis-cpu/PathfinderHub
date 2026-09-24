package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "open_answer_reviews", primaryKeys = ["attemptId", "questionId"])
data class OpenAnswerReviewEntity(
    val attemptId: String, val questionId: String, val answer: String,
    val score: Int?, val reviewedBy: String?, val reviewedAt: Date?
)
