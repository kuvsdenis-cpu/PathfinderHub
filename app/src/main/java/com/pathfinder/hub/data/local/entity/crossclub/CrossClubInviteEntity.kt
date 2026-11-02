package com.pathfinder.hub.data.local.entity.crossclub

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Явное приглашение клуба целиком (директор → клуб).
 *
 * targetType:
 *   "event" | "quest" | "activity" | "meeting"
 * status:
 *   "pending" | "accepted" | "declined" | "expired"
 */
@Entity(
    tableName = "cross_club_invites",
    indices = [Index("hostClubId"), Index("inviteeClubId"), Index("status")]
)
data class CrossClubInviteEntity(
    @PrimaryKey val id: String,
    val hostClubId: String,
    val inviteeClubId: String,
    val targetType: String,
    val targetId: String,
    val createdBy: String,
    val createdAt: Date,
    val expiresAt: Date? = null,
    val status: String = "pending",
    val pendingSync: Boolean = false,
)
