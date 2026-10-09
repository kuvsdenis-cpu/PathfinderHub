package com.pathfinder.hub.data.repository
import com.pathfinder.hub.data.local.dao.ParentDao
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.parent.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ParentRepository @Inject constructor(private val dao: ParentDao) {
    fun observeChildren(p: String): Flow<List<ParentChildLinkEntity>> = dao.observeChildren(p)
    fun observeParents(c: String): Flow<List<ParentChildLinkEntity>> = dao.observeParents(c)
    suspend fun upsertLink(l: ParentChildLinkEntity) = dao.upsertLink(l)
    suspend fun revokeLink(p: String, c: String, at: Long, r: String?) = dao.revokeLink(p, c, at, r)
    fun observeChildPayments(c: String): Flow<List<PaymentEntity>> = dao.observeChildPayments(c)
    fun observeParentPayments(p: String): Flow<List<PaymentEntity>> = dao.observeParentPayments(p)
    suspend fun upsertPayment(p: PaymentEntity) = dao.upsertPayment(p)
    suspend fun confirmPayment(id: String, s: String, b: String, at: Long) = dao.confirmPayment(id, s, b, at)
    fun observeMessages(p: String): Flow<List<ParentMessageEntity>> = dao.observeMessages(p)
    fun observePendingMessages(): Flow<List<ParentMessageEntity>> = dao.observePendingMessages()
    suspend fun upsertMessage(m: ParentMessageEntity) = dao.upsertMessage(m)
    suspend fun moderateMessage(id: String, s: String, b: String, at: Long) = dao.moderateMessage(id, s, b, at)
    suspend fun upsertMeeting(m: ParentMeetingEntity) = dao.upsertMeeting(m)
    fun observePWAConfig(): Flow<PWAConfigEntity?> = dao.observePWAConfig()
    suspend fun upsertPWAConfig(c: PWAConfigEntity) = dao.upsertPWAConfig(c)
    suspend fun logPWAInstall(e: PWAInstallEventEntity) = dao.logPWAInstall(e)
}
