package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "honor_progress",
    primaryKeys = ["userId", "requirementId"],
    indices = [Index("userId"), Index("honorId"), Index("status")])
data class HonorProgressEntity(
    val userId: String, val honorId: String, val requirementId: String,
    val status: String, val submittedAt: Date?, val approvedBy: String?,
    val approvedAt: Date?, val mediaUrls: List<String>, val comment: String?,
    val pendingSync: Boolean = false
)
