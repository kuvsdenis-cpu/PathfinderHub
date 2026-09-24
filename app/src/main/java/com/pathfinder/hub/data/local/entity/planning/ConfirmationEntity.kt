package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*
import java.util.Date

@Entity(tableName = "confirmations",
    indices = [Index("targetType", "targetId"), Index("status")])
data class ConfirmationEntity(
    @PrimaryKey val id: String, val targetType: String, val targetId: String,
    val requestedBy: String, val confirmedBy: String?, val status: String,
    val comment: String?, val createdAt: Date, val confirmedAt: Date?,
    val pendingSync: Boolean = false
)
