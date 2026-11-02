package com.pathfinder.hub.data.local.entity.crossclub

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Кто и когда воспользовался кодом cross-club.
 */
@Entity(
    tableName = "cross_club_invite_usages",
    indices = [Index("inviteCodeId"), Index("userId"), Index("userClubId")]
)
data class CrossClubInviteUsageEntity(
    @PrimaryKey val id: String,
    val inviteCodeId: String,
    val userId: String,
    val userClubId: String,
    val usedAt: Date,
)
