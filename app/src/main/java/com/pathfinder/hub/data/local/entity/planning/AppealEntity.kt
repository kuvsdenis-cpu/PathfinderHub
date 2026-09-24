package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*
import java.util.Date

@Entity(tableName = "appeals")
data class AppealEntity(
    @PrimaryKey val id: String, val moderationRequestId: String,
    val authorId: String, val text: String, val status: String,
    val reviewedBy: String?, val reviewedAt: Date?, val createdAt: Date,
    val pendingSync: Boolean = false
)
