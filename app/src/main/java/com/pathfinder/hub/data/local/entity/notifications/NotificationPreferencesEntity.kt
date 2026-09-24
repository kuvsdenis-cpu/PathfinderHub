package com.pathfinder.hub.data.local.entity.notifications
import androidx.room.*

@Entity(tableName = "notification_preferences")
data class NotificationPreferencesEntity(
    @PrimaryKey val userId: String, val quietHoursEnabled: Boolean,
    val quietHoursStart: Int, val quietHoursEnd: Int, val dailyPushLimit: Int,
    val pushEnabled: Boolean, val emailEnabled: Boolean, val digestEnabled: Boolean,
    val digestDay: String, val digestTime: String,
    val categoryTasks: Boolean, val categoryModeration: Boolean,
    val categoryLearning: Boolean, val categoryGamification: Boolean,
    val categoryEvents: Boolean, val categoryConsent: Boolean,
    val categoryProgress: Boolean, val categoryPayments: Boolean,
    val categoryContact: Boolean
)
