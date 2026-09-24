package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*
import java.util.Date

@Entity(tableName = "moderation_requests",
    indices = [Index("targetType", "targetId"), Index("status"), Index("clubId")])
data class ModerationRequestEntity(
    @PrimaryKey val id: String, val targetType: String, val targetId: String,
    val level: String, val clubId: String?, val requestedBy: String,
    val status: String, val autoModerationResult: String, val escalationLevel: Int,
    val escalatedTo: String?, val escalatedAt: Date?, val moderatedBy: String?,
    val moderatedAt: Date?, val hiddenReasonCode: String?, val hiddenReasonNote: String?,
    val appealDeadline: Date?, val createdAt: Date
)
