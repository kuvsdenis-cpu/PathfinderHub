package com.pathfinder.hub.data.local.entity.crossclub

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Код приглашения для teen → teen (cross-club).
 *
 * maxUses = -1 — без лимита.
 * expiresAt = null — бессрочный.
 * status:
 *   "active" | "expired" | "revoked"
 */
@Entity(
    tableName = "cross_club_invite_codes",
    indices = [Index("code", unique = true), Index("hostClubId"), Index("createdBy"), Index("status")]
)
data class CrossClubInviteCodeEntity(
    @PrimaryKey val id: String,
    val code: String,
    val hostClubId: String,
    val targetType: String,
    val targetId: String,
    val createdBy: String,
    val maxUses: Int = 50,
    val usedCount: Int = 0,
    val expiresAt: Date? = null,
    val status: String = "active",
    val createdAt: Date,
    val pendingSync: Boolean = false,
)
