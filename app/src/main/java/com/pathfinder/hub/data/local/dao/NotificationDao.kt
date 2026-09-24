package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.notifications.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<NotificationEntity>>
    @Query("SELECT * FROM notifications WHERE userId = :userId AND isRead = 0")
    fun observeUnread(userId: String): Flow<List<NotificationEntity>>
    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    fun observeUnreadCount(userId: String): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(n: NotificationEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(ns: List<NotificationEntity>)
    @Query("UPDATE notifications SET isRead = 1, readAt = :at WHERE id = :id")
    suspend fun markRead(id: String, at: Long)
    @Query("UPDATE notifications SET isRead = 1, readAt = :at WHERE userId = :userId")
    suspend fun markAllRead(userId: String, at: Long)
    @Query("UPDATE notifications SET isHandled = 1, handledAt = :at WHERE id = :id")
    suspend fun markHandled(id: String, at: Long)
    @Query("DELETE FROM notifications WHERE createdAt < :before")
    suspend fun purgeOld(before: Long)
    @Query("SELECT * FROM notification_preferences WHERE userId = :userId")
    fun observePrefs(userId: String): Flow<NotificationPreferencesEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrefs(p: NotificationPreferencesEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToken(t: FCMTokenEntity)
    @Query("DELETE FROM fcm_tokens WHERE userId = :userId AND deviceId != :deviceId")
    suspend fun clearOtherTokens(userId: String, deviceId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalytics(a: NotificationAnalyticsEntity)
    @Query("UPDATE notification_analytics SET openedAt = :at WHERE notificationId = :id")
    suspend fun markOpened(id: String, at: Long)
}
