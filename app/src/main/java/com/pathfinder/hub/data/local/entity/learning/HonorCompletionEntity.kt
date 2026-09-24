package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "honor_completions", primaryKeys = ["userId", "honorId"])
data class HonorCompletionEntity(
    val userId: String, val honorId: String, val completedAt: Date, val approvedBy: String
)
