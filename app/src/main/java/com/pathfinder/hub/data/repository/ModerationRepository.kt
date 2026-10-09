package com.pathfinder.hub.data.repository

import android.util.Log
import com.pathfinder.hub.data.local.dao.ModerationDao
import com.pathfinder.hub.data.local.entity.planning.*
import com.pathfinder.hub.data.remote.FirestoreCommentDataSource
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Репозиторий модерации и комментариев.
 *
 * Архитектура (Вариант Б):
 *   - Firestore — источник правды для комментариев (snapshot listener, реальное время).
 *   - Room — локальный кэш для офлайн-чтения и для работы модерации.
 *
 * Запись:
 *   sendComment() → Firestore + Room (сразу, синхронно)
 *   Если Firestore упал — комментарий уходит в SyncQueue на повтор.
 *
 * Чтение для UI:
 *   observeApprovedCommentsFromFirestore() → Firestore snapshot listener
 *
 * Модерация (ручная):
 *   Работает через Room. Результат модерации записывается в Room,
 *   затем отдельно синхронизируется в Firestore (при необходимости).
 */
class ModerationRepository @Inject constructor(
    private val dao: ModerationDao,
    private val firestore: FirestoreCommentDataSource,
    private val syncQueue: SyncQueueManager,
) {

    companion object {
        private const val TAG = "ModerationRepository"
        private const val ENTITY_TYPE_COMMENT = "comment"
        private const val OP_UPSERT = "upsert"
    }

    // ==================== ЧТЕНИЕ (Firestore) ====================

    /**
     * Наблюдает за опубликованными комментариями через Firestore в реальном времени.
     * Используется в UI чата и комментариев.
     *
     * @param clubId клуб (обязательно)
     * @param targetType "club_chat" | "event" | "task" | ...
     * @param targetId id объекта (для чата = clubId)
     */
    fun observeApprovedCommentsFromFirestore(
        clubId: String,
        targetType: String,
        targetId: String,
    ): Flow<List<CommentEntity>> =
        firestore.observeApprovedComments(clubId, targetType, targetId)

    // ==================== ЧТЕНИЕ (Room, кэш) ====================

    fun observeApprovedComments(t: String, id: String): Flow<List<CommentEntity>> =
        dao.observeApprovedComments(t, id)

    fun observeUserComments(u: String): Flow<List<CommentEntity>> =
        dao.observeUserComments(u)

    /**
     * Очередь ручной модерации — источник правды Firestore.
     * Room-версия оставлена рядом (observePendingCommentsLocal)
     * для офлайн-фолбэка / тестов.
     */
    fun observePendingComments(clubId: String): Flow<List<CommentEntity>> =
        firestore.observePendingComments(clubId)

    /** Локальный кэш из Room (автор увидит свои же pending-комментарии офлайн). */
    fun observePendingCommentsLocal(): Flow<List<CommentEntity>> =
        dao.observePendingComments()

    // ==================== ЗАПИСЬ (Firestore + Room) ====================

    /**
     * Записывает комментарий сразу в Firestore и в Room.
     *
     * Firestore — источник правды. Room — кэш.
     * Если Firestore упал — пишем в Room (пользователь увидит локально)
     * и ставим в SyncQueue, чтобы SyncWorker повторил отправку позже.
     *
     * Сетевой вызов обёрнут в NonCancellable: даже если ViewModel/скоуп умерли
     * (пользователь ушёл с экрана), запись в Firestore дойдёт до конца.
     * Это устраняет потерю комментариев при JobCancellationException.
     */
    suspend fun sendComment(comment: CommentEntity): Result<Unit> {
        // 1. Firestore — не отменяем, даже если скоуп умер
        val remoteResult = withContext(NonCancellable) {
            firestore.sendComment(comment)
        }
        remoteResult.fold(
            onSuccess = { Log.d(TAG, "sendComment OK: ${comment.id}") },
            onFailure = { e -> Log.w(TAG, "sendComment FAILED, пишем локально", e) }
        )

        // 2. Room (кэш). pendingSync = true, если Firestore не принял
        dao.upsertComment(comment.copy(pendingSync = remoteResult.isFailure))

        // 3. SyncQueue — повторная отправка, если Firestore не принял
        if (remoteResult.isFailure) {
            try {
                syncQueue.enqueue(
                    entityType = ENTITY_TYPE_COMMENT,
                    entityId = comment.id,
                    operation = OP_UPSERT,
                    payload = comment
                )
                Log.d(TAG, "sendComment → в очередь на повтор: ${comment.id}")
            } catch (e: Exception) {
                Log.e(TAG, "sendComment: не удалось поставить в очередь ${comment.id}", e)
            }
        }

        return remoteResult
    }

    /**
     * Старый метод upsertComment — для обратной совместимости
     * (используется в CommentsViewModel.attachFile и в CreateCommentUseCase).
     */
    suspend fun upsertComment(c: CommentEntity) = dao.upsertComment(c)

    // ==================== МОДЕРАЦИЯ (Room) ====================

    suspend fun moderateComment(
        id: String,
        s: String,
        b: String,
        at: Long,
        r: String?,
    ) = dao.moderateComment(id, s, b, at, r)

    fun observePendingRequests(c: String): Flow<List<ModerationRequestEntity>> =
        dao.observePendingRequests(c)

    suspend fun upsertRequest(r: ModerationRequestEntity) = dao.upsertRequest(r)

    suspend fun resolveRequest(
        id: String,
        s: String,
        b: String,
        at: Long,
        r: String?,
    ) = dao.resolveRequest(id, s, b, at, r)

    suspend fun escalate(id: String, l: Int, t: String, at: Long) =
        dao.escalate(id, l, t, at)

    fun observePendingAppeals(): Flow<List<AppealEntity>> = dao.observePendingAppeals()

    suspend fun upsertAppeal(a: AppealEntity) = dao.upsertAppeal(a)

    suspend fun resolveAppeal(id: String, s: String, b: String, at: Long) =
        dao.resolveAppeal(id, s, b, at)

    suspend fun upsertHistory(h: ModerationHistoryEntity) = dao.upsertHistory(h)

    // ==================== УДАЛЕНИЕ ====================

    suspend fun deleteComment(clubId: String, commentId: String): Result<Unit> {
        val remoteResult = firestore.deleteComment(clubId, commentId)
        // В Room — при необходимости, через DAO
        // dao.deleteComment(commentId) — если метод есть
        return remoteResult
    }
}