package com.pathfinder.hub.data.local.entity.core
import androidx.room.*
import java.util.Date

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String, val email: String, val emailVerified: Boolean,
    val firstName: String, val lastName: String, val role: String,
    val clubId: String?, val conferenceId: String?, val birthDate: Date,
    val avatar: String?, val parentEmail: String?, val parentalConsentStatus: String,
    val parentalConsentRequestedAt: Date?, val parentalConsentApprovedAt: Date?,
    val accessLevel: String, val status: String, val deletionRequestedBy: String?,
    val deletionRequestedAt: Date?, val anonymizedAt: Date?,
    val permanentDeletionAt: Date?, val onboardingCompleted: Boolean,
    val createdAt: Date, val lastLoginAt: Date?, val syncedAt: Date? = null
)
