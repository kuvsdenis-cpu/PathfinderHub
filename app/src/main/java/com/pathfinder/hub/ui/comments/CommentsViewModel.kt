package com.pathfinder.hub.ui.comments

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.CommentEntity
import com.pathfinder.hub.data.repository.ModerationRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.service.AutoModerationService
import com.pathfinder.hub.ui.storage.FileUploadResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class CommentUiModel(
    val id: String,
    val authorName: String,
    val authorInitials: String,
    val text: String,
    val createdAt: Date,
    val isOwn: Boolean,
    val status: String,
    val attachments: List<String> = emptyList()
)

data class CommentsUiState(
    val comments: List<CommentUiModel> = emptyList(),
    val inputText: String = "",
    val currentUserId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isSending: Boolean = false
)

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val moderationRepository: ModerationRepository,
    private val autoModerationService: AutoModerationService,
) : ViewModel() {

    companion object {
        private const val TAG = "CommentsViewModel"
        private const val CLUB_CHAT_TARGET = "club_chat"
        private const val RETRY_DELAY_MS = 3_000L
    }

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var currentTargetType: String? = null
    private var currentTargetId: String? = null
    private var currentClubId: String? = null

    /**
     * Инициализация. Вызывается из CommentsSection при первом показе.
     *
     * @param targetType "club_chat" | "event" | "task" | ...
     * @param targetId   id объекта. Для чата = clubId, для события = eventId
     * @param clubId     клуб. Для чата = targetId, для события — clubId события.
     */
    fun init(targetType: String, targetId: String, clubId: String) {
        if (currentTargetType == targetType
            && currentTargetId == targetId
            && currentClubId == clubId
        ) return

        currentTargetType = targetType
        currentTargetId = targetId
        currentClubId = clubId

        val userId = sessionManager.getUserId()
        _state.update { it.copy(currentUserId = userId) }

        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }

        if (clubId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
            return
        }

        viewModelScope.launch {
            // Цикл переподписки: если listener упал (нет сети, нет индекса) —
            // ждём и подписываемся заново, пока ViewModel жива.
            while (isActive) {
                try {
                    moderationRepository
                        .observeApprovedCommentsFromFirestore(clubId, targetType, targetId)
                        .catch { e ->
                            Log.e(TAG, "Firestore listener failed, retry in ${RETRY_DELAY_MS}ms", e)
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Нет связи, переподключение…"
                                )
                            }
                            emit(emptyList())
                        }
                        .collect { list ->
                            val uiModels = list.map { c ->
                                val author = userRepository.getUser(c.authorId)
                                val initials = author?.let {
                                    "${it.firstName.firstOrNull()?.uppercase() ?: ""}${it.lastName.firstOrNull()?.uppercase() ?: ""}"
                                } ?: "?"
                                CommentUiModel(
                                    id = c.id,
                                    authorName = author?.let { "${it.firstName} ${it.lastName}" } ?: "Аноним",
                                    authorInitials = initials,
                                    text = c.text,
                                    createdAt = c.createdAt,
                                    isOwn = c.authorId == userId,
                                    status = c.status,
                                    attachments = c.attachments
                                )
                            }
                            _state.update {
                                it.copy(
                                    comments = uiModels,
                                    isLoading = false,
                                    errorMessage = null
                                )
                            }
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "listener loop error", e)
                    _state.update {
                        it.copy(isLoading = false, errorMessage = e.message)
                    }
                }
                delay(RETRY_DELAY_MS)
            }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun sendComment() {
        val s = _state.value
        val text = s.inputText.trim()
        if (text.isBlank()) return

        val userId = s.currentUserId ?: return
        val targetType = currentTargetType ?: return
        val targetId = currentTargetId ?: return
        val clubId = currentClubId ?: return

        _state.update { it.copy(isSending = true) }

        viewModelScope.launch {
            try {
                publishComment(
                    userId = userId,
                    clubId = clubId,
                    targetType = targetType,
                    targetId = targetId,
                    text = text,
                    attachments = emptyList()
                )
                _state.update { it.copy(inputText = "", isSending = false) }
            } catch (e: Exception) {
                Log.e(TAG, "sendComment failed", e)
                _state.update { it.copy(isSending = false, errorMessage = e.message) }
            }
        }
    }

    /**
     * Прикрепление файла.
     *
     * Файлы не проходят автомодерацию — timestamp в имени может ложно
     * срабатывать на phoneRegex. Публикуем сразу.
     */
    fun attachFile(result: FileUploadResult) {
        val userId = _state.value.currentUserId ?: sessionManager.getUserId() ?: return
        val targetType = currentTargetType ?: return
        val targetId = currentTargetId ?: return
        val clubId = currentClubId ?: return

        _state.update { it.copy(isSending = true) }

        viewModelScope.launch {
            try {
                val now = Date()
                val comment = CommentEntity(
                    id = UUID.randomUUID().toString(),
                    targetType = targetType,
                    targetId = targetId,
                    clubId = clubId,
                    authorId = userId,
                    text = result.fileName,
                    attachments = listOf(result.fileUrl),
                    status = "published",
                    autoModerationResult = "skipped_attachment",
                    publishedAt = now,
                    reviewDeadline = null,
                    moderatedBy = null,
                    moderatedAt = null,
                    hiddenReasonCode = null,
                    hiddenReasonNote = null,
                    createdAt = now,
                    pendingSync = false
                )
                moderationRepository.sendComment(comment)
                _state.update { it.copy(isSending = false) }
            } catch (e: Exception) {
                Log.e(TAG, "attachFile failed", e)
                _state.update { it.copy(isSending = false, errorMessage = e.message) }
            }
        }
    }

    private suspend fun publishComment(
        userId: String,
        clubId: String,
        targetType: String,
        targetId: String,
        text: String,
        attachments: List<String>,
    ) {
        val autoResult = autoModerationService.check(text)
        val now = Date()
        val reviewDeadline = if (autoResult == AutoModerationService.RESULT_PASSED) {
            Date(now.time + 6 * 60 * 60 * 1000L)
        } else null

        val status = when (autoResult) {
            AutoModerationService.RESULT_PASSED -> "published"
            AutoModerationService.RESULT_FLAGGED -> "pending_review"
            AutoModerationService.RESULT_BLOCKED -> "rejected"
            else -> "pending_review"
        }

        val comment = CommentEntity(
            id = UUID.randomUUID().toString(),
            targetType = targetType,
            targetId = targetId,
            clubId = clubId,
            authorId = userId,
            text = text,
            attachments = attachments,
            status = status,
            autoModerationResult = autoResult,
            publishedAt = if (status == "published") now else null,
            reviewDeadline = reviewDeadline,
            moderatedBy = null,
            moderatedAt = null,
            hiddenReasonCode = null,
            hiddenReasonNote = null,
            createdAt = now,
            pendingSync = false
        )

        moderationRepository.sendComment(comment)
    }
}