package com.pathfinder.hub.data.repository
import com.pathfinder.hub.data.local.dao.NotificationDao
import com.pathfinder.hub.data.local.entity.notifications.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NotificationRepository @Inject constructor(private val dao: NotificationDao) {
    fun observeAll(u: String): Flow<List<NotificationEntity>> = dao.observeAll(u)
    fun observeUnread(u: String): Flow<List<NotificationEntity>> = dao.observeUnread(u)
    fun observeUnreadCount(u: String): Flow<Int> = dao.observeUnreadCount(u)
    suspend fun upsert(n: NotificationEntity) = dao.upsert(n)
    suspend fun upsertAll(ns: List<NotificationEntity>) = dao.upsertAll(ns)
    suspend fun markRead(id: String, at: Long) = dao.markRead(id, at)
    suspend fun markAllRead(u: String, at: Long) = dao.markAllRead(u, at)
    suspend fun markHandled(id: String, at: Long) = dao.markHandled(id, at)
    suspend fun purgeOld(before: Long) = dao.purgeOld(before)
    fun observePrefs(u: String): Flow<NotificationPreferencesEntity?> = dao.observePrefs(u)
    suspend fun upsertPrefs(p: NotificationPreferencesEntity) = dao.upsertPrefs(p)
    suspend fun upsertToken(t: FCMTokenEntity) = dao.upsertToken(t)
    suspend fun clearOtherTokens(u: String, d: String) = dao.clearOtherTokens(u, d)
    suspend fun upsertAnalytics(a: NotificationAnalyticsEntity) = dao.upsertAnalytics(a)
    suspend fun markOpened(id: String, at: Long) = dao.markOpened(id, at)
}
