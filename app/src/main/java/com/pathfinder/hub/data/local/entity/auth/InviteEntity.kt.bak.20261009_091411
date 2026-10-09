package com.pathfinder.hub.data.local.entity.auth
import androidx.room.*
import java.util.Date

@Entity(tableName = "invites")
data class InviteEntity(
    @PrimaryKey val id: String, val code: String, val clubId: String,
    val role: String, val createdBy: String, val createdAt: Date,
    val expiresAt: Date, val status: String, val usedBy: String?, val usedAt: Date?
)
