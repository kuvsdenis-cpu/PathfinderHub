package com.pathfinder.hub.data.local.entity.auth
import androidx.room.*
import java.util.Date

@Entity(tableName = "invites")
data class InviteEntity(
    @PrimaryKey val id: String, val code: String, val clubId: String,
    val conferenceId: String? = null,
    val role: String,
    val permissions: List<String> = emptyList(),
    val createdBy: String, val createdAt: Date,
    val expiresAt: Date, val status: String, val usedBy: String?, val usedAt: Date?
)
