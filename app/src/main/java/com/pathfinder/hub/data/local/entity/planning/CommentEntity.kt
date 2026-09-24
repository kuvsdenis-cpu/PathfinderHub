package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*
import java.util.Date

@Entity(tableName = "comments",
    indices = [Index("targetType", "targetId"), Index("authorId"), Index("status")])
data class CommentEntity(
    @PrimaryKey val id: String, val targetType: String, val targetId: String,
    val authorId: String, val text: String, val attachments: List<String>,
    val status: String, val autoModerationResult: String,
    val publishedAt: Date?, val reviewDeadline: Date?, val moderatedBy: String?,
    val moderatedAt: Date?, val hiddenReasonCode: String?, val hiddenReasonNote: String?,
    val createdAt: Date, val pendingSync: Boolean = false
)
