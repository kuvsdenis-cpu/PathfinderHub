package com.pathfinder.hub.data.local.entity.core
import androidx.room.*
import java.util.Date

@Entity(tableName = "clubs")
data class ClubEntity(
    @PrimaryKey val id: String, val conferenceId: String, val name: String,
    val logoUrl: String?, val city: String, val region: String,
    val contactEmail: String, val contactPhone: String, val foundedAt: Date?,
    val motto: String?, val meetingDay: String, val startTime: String,
    val endTime: String, val location: String, val frequency: String,
    val autoAcceptInvite: Boolean, val inviteLifetimeDays: Int,
    val requireParentalConsent: Boolean, val maxMembers: Int?,
    val paymentEnabled: Boolean, val membershipFee: Double?,
    val membershipPeriod: String?, val paymentMethods: List<String>,
    val paymentRemindersEnabled: Boolean, val autoModerationEnabled: Boolean,
    val reviewWindowHours: Int, val escalationHours: Int,
    val defaultReportTemplateId: String?, val reportPeriods: List<String>,
    val autoGenerateReports: Boolean, val updatedAt: Date,
    val updatedBy: String, val syncedAt: Date? = null
)
