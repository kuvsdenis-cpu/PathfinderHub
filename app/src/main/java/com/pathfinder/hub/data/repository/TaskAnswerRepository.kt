package com.pathfinder.hub.data.repository

import android.util.Log
import com.pathfinder.hub.data.local.dao.RequirementTaskAnswerDao
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.remote.FirestoreTaskAnswerDataSource
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий ответов на проверочные задания.
 *
 * - Следопыт: saveDraft, submit.
 * - Наставник/директор: reviewQueue, approve, reject.
 */
@Singleton
class TaskAnswerRepository @Inject constructor(
    private val dao: RequirementTaskAnswerDao,
    private val firestore: FirestoreTaskAnswerDataSource,
    private val syncQueue: SyncQueueManager,
) {

    companion object {
        private const val TAG = "TaskAnswerRepo"
        private const val ET_ANSWER = "requirement_task_answer"
    }

    // ==================== ЧТЕНИЕ ====================

    fun observeMyAnswers(clubId: String, userId: String): Flow<List<RequirementTaskAnswerEntity>> =
        firestore.observeMyAnswers(clubId, userId)

    fun observeMyAnswer(clubId: String, userId: String, taskId: String): Flow<RequirementTaskAnswerEntity?> =
        firestore.observeMyAnswer(clubId, userId, taskId)

    fun observeReviewQueue(clubId: String): Flow<List<RequirementTaskAnswerEntity>> =
        firestore.observeReviewQueue(clubId)

    suspend fun getAnswer(userId: String, taskId: String): RequirementTaskAnswerEntity? =
        dao.getByTaskAndUser(taskId, userId)

    suspend fun countApproved(requirementId: String, userId: String): Int =
        dao.countApprovedForRequirement(requirementId, userId)

    // ==================== ЗАПИСЬ (следопыт) ====================

    /**
     * Сохранить черновик (не отправляет на проверку).
     */
    suspend fun saveDraft(
        clubId: String, userId: String, requirementId: String, taskId: String,
        answer: String, attachments: List<String>,
    ): Result<Unit> {
        val now = Date()
        val existing = dao.getByTaskAndUser(taskId, userId)
        val entity = RequirementTaskAnswerEntity(
            id = existing?.id ?: "${userId}_${taskId}",
            taskId = taskId, requirementId = requirementId,
            userId = userId, clubId = clubId,
            answer = answer, attachments = attachments,
            status = RequirementTaskAnswerEntity.STATUS_DRAFT,
            autoChecked = false,
            submittedAt = existing?.submittedAt,
            reviewedBy = existing?.reviewedBy,
            reviewedAt = existing?.reviewedAt,
            reviewComment = existing?.reviewComment,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            pendingSync = true,
        )
        return save(entity)
    }

    /**
     * Отправить на проверку. Если задание с автопроверкой и ответ верный,
     * сразу ставим статус auto_approved.
     */
    suspend fun submit(
        clubId: String, userId: String, requirementId: String, taskId: String,
        answer: String, attachments: List<String>,
        autoCheck: Boolean, correctAnswer: String?,
    ): Result<RequirementTaskAnswerEntity> {
        val now = Date()
        val existing = dao.getByTaskAndUser(taskId, userId)

        val passedAuto = autoCheck && correctAnswer != null &&
            answer.trim().equals(correctAnswer.trim(), ignoreCase = true)

        val status = if (passedAuto)
            RequirementTaskAnswerEntity.STATUS_AUTO_APPROVED
        else
            RequirementTaskAnswerEntity.STATUS_SUBMITTED

        val entity = RequirementTaskAnswerEntity(
            id = existing?.id ?: "${userId}_${taskId}",
            taskId = taskId, requirementId = requirementId,
            userId = userId, clubId = clubId,
            answer = answer, attachments = attachments,
            status = status,
            autoChecked = passedAuto,
            submittedAt = now,
            reviewedBy = null, reviewedAt = null, reviewComment = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            pendingSync = true,
        )

        val saveResult = save(entity)
        return if (saveResult.isSuccess) Result.success(entity)
        else Result.failure(saveResult.exceptionOrNull() ?: Exception("save failed"))
    }

    // ==================== ПРОВЕРКА (наставник/директор) ====================

    suspend fun approve(
        clubId: String, userId: String, taskId: String,
        reviewerId: String,
    ): Result<Unit> {
        val existing = dao.getByTaskAndUser(taskId, userId)
            ?: return Result.failure(Exception("Ответ не найден"))
        val updated = existing.copy(
            status = RequirementTaskAnswerEntity.STATUS_APPROVED,
            reviewedBy = reviewerId, reviewedAt = Date(),
            reviewComment = null,
            updatedAt = Date(), pendingSync = true,
        )
        dao.upsert(updated)
        return pushAndClearQueue(updated)
    }

    suspend fun reject(
        clubId: String, userId: String, taskId: String,
        reviewerId: String, comment: String,
    ): Result<Unit> {
        if (comment.isBlank()) return Result.failure(Exception("Комментарий обязателен"))
        val existing = dao.getByTaskAndUser(taskId, userId)
            ?: return Result.failure(Exception("Ответ не найден"))
        val updated = existing.copy(
            status = RequirementTaskAnswerEntity.STATUS_REJECTED,
            reviewedBy = reviewerId, reviewedAt = Date(),
            reviewComment = comment,
            updatedAt = Date(), pendingSync = true,
        )
        dao.upsert(updated)
        return pushAndClearQueue(updated)
    }

    // ==================== ВНУТРЕННЕЕ ====================

    private suspend fun save(a: RequirementTaskAnswerEntity): Result<Unit> {
        dao.upsert(a)
        val remote = firestore.upsertAnswer(a)
        if (remote.isSuccess) {
            dao.clearPendingSync(a.id)
        } else {
            syncQueue.enqueue(ET_ANSWER, a.id, "upsert", a)
            Log.w(TAG, "answer saved locally, queued for sync")
        }
        return remote
    }

    private suspend fun pushAndClearQueue(a: RequirementTaskAnswerEntity): Result<Unit> {
        val remote = firestore.reviewAnswer(a)
        if (remote.isSuccess) {
            dao.clearPendingSync(a.id)
        } else {
            syncQueue.enqueue(ET_ANSWER, a.id, "upsert", a)
        }
        return remote
    }
}
