package com.pathfinder.hub.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.CommentEntity
import com.pathfinder.hub.data.repository.ModerationRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.service.AutoModerationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val status: String
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
    private val autoModerationService: AutoModerationService
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var currentTargetType: String? = null
    private var currentTargetId: String? = null

    fun init(targetType: String, targetId: String) {
        if (currentTargetType == targetType && currentTargetId == targetId) return
        currentTargetType = targetType
        currentTargetId = targetId

        val userId = sessionManager.getUserId()
        _state.update { it.copy(currentUserId = userId) }

        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }

        viewModelScope.launch {
            try {
                moderationRepository.observeApprovedComments(targetType, targetId).collect { list ->
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
                            status = c.status
                        )
                    }
                    _state.update { it.copy(comments = uiModels, isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
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

        val userId = s.currentUserId
        val targetType = currentTargetType
        val targetId = currentTargetId
        if (userId == null || targetType == null || targetId == null) return

        _state.update { it.copy(isSending = true) }

        viewModelScope.launch {
            try {
                val autoResult = autoModerationService.check(text)
                val now = Date()
                val reviewDeadline = if (autoResult == "ok") {
                    Date(now.time + 6 * 60 * 60 * 1000L) // 6 часов
                } else null

                val status = when (autoResult) {
                    "ok" -> "published"
                    else -> "pending_review"
                }

                val comment = CommentEntity(
                    id = UUID.randomUUID().toString(),
                    targetType = targetType,
                    targetId = targetId,
                    authorId = userId,
                    text = text,
                    attachments = emptyList(),
                    status = status,
                    autoModerationResult = autoResult,
                    publishedAt = if (status == "published") now else null,
                    reviewDeadline = reviewDeadline,
                    moderatedBy = null,
                    moderatedAt = null,
                    hiddenReasonCode = null,
                    hiddenReasonNote = null,
                    createdAt = now,
                    pendingSync = true
                )

                moderationRepository.upsertComment(comment)
                _state.update { it.copy(inputText = "", isSending = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isSending = false, errorMessage = e.message) }
            }
        }
    }
}