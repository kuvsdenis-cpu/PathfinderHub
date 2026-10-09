package com.pathfinder.hub.data.local.entity.learning

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Ответ следопыта на проверочное задание.
 *
 * Статусы:
 *   "draft"         - начал, не отправил
 *   "submitted"     - отправлено на ручную проверку
 *   "auto_approved" - автопроверка прошла
 *   "approved"      - наставник/директор одобрил
 *   "rejected"      - наставник/директор отклонил (reviewComment обязателен)
 */
@Entity(
    tableName = "requirement_task_answers",
    indices = [
        Index("taskId"),
        Index("requirementId"),
        Index("userId"),
        Index("clubId"),
        Index("status"),
        Index("taskId", "userId", unique = true)
    ]
)
data class RequirementTaskAnswerEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val requirementId: String,
    val userId: String,
    val clubId: String,
    val answer: String,
    val attachments: List<String> = emptyList(),
    val status: String,
    val autoChecked: Boolean = false,
    val submittedAt: Date? = null,
    val reviewedBy: String? = null,
    val reviewedAt: Date? = null,
    val reviewComment: String? = null,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false
) {
    companion object {
        const val STATUS_DRAFT = "draft"
        const val STATUS_SUBMITTED = "submitted"
        const val STATUS_AUTO_APPROVED = "auto_approved"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"

        val DONE_STATUSES = setOf(STATUS_APPROVED, STATUS_AUTO_APPROVED)
    }
}
