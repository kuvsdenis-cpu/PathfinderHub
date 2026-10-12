package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.EventConsentDao
import com.pathfinder.hub.data.local.dao.ParentDao
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.parent.EventConsentEntity
import com.pathfinder.hub.data.local.entity.parent.ParentMeetingEntity
import com.pathfinder.hub.data.local.entity.parent.ParentMessageEntity
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Родительский репозиторий.
 *
 * Сигнатуры соответствуют вызовам из use-case'ов:
 *   - ConfirmPaymentUseCase: confirmPayment(id, status, by, at)
 *   - LinkChildUseCase:      upsertLink(entity)
 *   - SendParentMessageUseCase: upsertMessage(entity)
 *   - revokeLink, moderateMessage, upsertMeeting, upsertPayment — на будущее
 */
@Singleton
class ParentRepository @Inject constructor(
    private val dao: ParentDao,
    private val consentDao: EventConsentDao,
) {

    // ---------- Дети ----------
    fun observeChildren(parentId: String): Flow<List<ParentChildLinkEntity>> =
        dao.observeChildren(parentId)

    fun observeParents(childId: String): Flow<List<ParentChildLinkEntity>> =
        dao.observeParents(childId)

    suspend fun upsertLink(l: ParentChildLinkEntity) = dao.upsertLink(l)

    suspend fun revokeLink(
        parentId: String,
        childId: String,
        at: Long = System.currentTimeMillis(),
        reason: String? = null,
    ) = dao.revokeLink(parentId, childId, at, reason)

    // ---------- Оплаты ----------
    fun observeChildPayments(childId: String): Flow<List<PaymentEntity>> =
        dao.observeChildPayments(childId)

    fun observeParentPayments(parentId: String): Flow<List<PaymentEntity>> =
        dao.observeParentPayments(parentId)

    suspend fun upsertPayment(p: PaymentEntity) = dao.upsertPayment(p)

    suspend fun confirmPayment(
        id: String,
        status: String,
        by: String,
        at: Long = System.currentTimeMillis(),
    ) = dao.confirmPayment(id, status, by, at)

    // ---------- Сообщения ----------
    fun observeMessages(parentId: String): Flow<List<ParentMessageEntity>> =
        dao.observeMessages(parentId)

    fun observePendingMessages(): Flow<List<ParentMessageEntity>> =
        dao.observePendingMessages()

    suspend fun upsertMessage(m: ParentMessageEntity) = dao.upsertMessage(m)

    suspend fun moderateMessage(
        id: String,
        status: String,
        by: String,
        at: Long = System.currentTimeMillis(),
    ) = dao.moderateMessage(id, status, by, at)

    // ---------- Встречи ----------
    suspend fun upsertMeeting(m: ParentMeetingEntity) = dao.upsertMeeting(m)

    fun observeAllMeetings(): Flow<List<ParentMeetingEntity>> =
        dao.observeAllMeetings()

    suspend fun getMeetingByEventId(eventId: String): ParentMeetingEntity? =
        dao.getMeetingByEventId(eventId)

    // ---------- Согласия на мероприятия ----------
    fun observeConsentsByParent(parentId: String): Flow<List<EventConsentEntity>> =
        consentDao.observeByParent(parentId)

    fun observeConsentsByEvent(eventId: String): Flow<List<EventConsentEntity>> =
        consentDao.observeByEvent(eventId)

    suspend fun getConsent(eventId: String, childId: String, parentId: String): EventConsentEntity? =
        consentDao.getOne(eventId, childId, parentId)

    suspend fun countPendingConsents(eventId: String): Int =
        consentDao.countPendingByEvent(eventId)

    suspend fun upsertConsent(c: EventConsentEntity) = consentDao.upsert(c)

    suspend fun upsertConsents(cs: List<EventConsentEntity>) = consentDao.upsertAll(cs)
}
