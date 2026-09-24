package com.pathfinder.hub.data.local.entity.newspaper
import androidx.room.*
import java.util.Date

@Entity(tableName = "newspaper_moderations")
data class NewspaperModerationEntity(
    @PrimaryKey val issueId: String, val requestedBy: String,
    val requestedAt: Date, val reviewedBy: String?, val reviewedAt: Date?,
    val status: String, val rejectionReason: String?
)
