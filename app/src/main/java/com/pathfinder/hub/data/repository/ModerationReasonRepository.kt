package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.ModerationReasonDao
import com.pathfinder.hub.data.local.entity.planning.ModerationReasonEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ModerationReasonRepository @Inject constructor(
    private val dao: ModerationReasonDao,
    private val syncQueueManager: SyncQueueManager,
) {
    companion object {
        val SYSTEM_REASONS: List<ModerationReasonEntity> = listOf(
            ModerationReasonEntity("spam", "Спам", "Реклама, навязчивые ссылки, массовые рассылки", true, 1, true),
            ModerationReasonEntity("off_topic", "Не по теме", "Сообщение не относится к теме обсуждения", true, 2, true),
            ModerationReasonEntity("inappropriate", "Неприемлемое содержание", "Оскорбления, взрослый контент", true, 3, true),
            ModerationReasonEntity("profanity", "Нецензурная лексика", "Мат, грубые выражения", true, 4, true),
            ModerationReasonEntity("advertising", "Реклама", "Продвижение товаров и услуг", true, 5, true),
            ModerationReasonEntity("duplicate", "Дубликат", "Повторяющееся сообщение", true, 6, true),
            ModerationReasonEntity("low_quality", "Низкое качество", "Бессмысленный, пустой контент", true, 7, true),
            ModerationReasonEntity("review_timeout", "Истёк срок проверки", "Автоматическое скрытие по таймауту", true, 8, true),
            ModerationReasonEntity("custom", "Другое", "Причина указана вручную", true, 9, true),
        )
    }

    fun observeAll(): Flow<List<ModerationReasonEntity>> = dao.observeAll()
    fun observeActive(): Flow<List<ModerationReasonEntity>> = dao.observeActive()
    suspend fun getByCode(code: String): ModerationReasonEntity? = dao.getByCode(code)

    suspend fun upsert(reason: ModerationReasonEntity) {
        dao.upsert(reason)
        // Баг 0.2: без очереди изменение не уйдёт в Firestore.
        syncQueueManager.enqueue("moderation_reason", reason.code, "upsert", reason)
    }

    suspend fun setActive(code: String, active: Boolean) {
        val r = dao.getByCode(code) ?: return
        val updated = r.copy(isActive = active)
        dao.upsert(updated)
        syncQueueManager.enqueue("moderation_reason", code, "UPDATE", updated)
    }

    suspend fun deleteCustom(code: String): Boolean {
        val r = dao.getByCode(code) ?: return false
        if (r.isSystem) return false
        val deleted = dao.deleteCustom(code) > 0
        if (deleted) {
            syncQueueManager.enqueue("moderation_reason", code, "delete", r)
        }
        return deleted
    }

    suspend fun ensureSeed() {
        if (dao.count() == 0) {
            SYSTEM_REASONS.forEach { dao.upsert(it) }
        }
    }
}
