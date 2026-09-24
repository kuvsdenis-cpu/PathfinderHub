package com.pathfinder.hub.data.local.entity.auth
import androidx.room.*
import java.util.Date

@Entity(tableName = "temporary_passwords")
data class TemporaryPasswordEntity(
    @PrimaryKey val userId: String, val createdBy: String,
    val createdAt: Date, val expiresAt: Date, val used: Boolean
)
