package com.pathfinder.hub.ui.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.CommentEntity
import com.pathfinder.hub.data.repository.ModerationRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentToModerate(
    val comment: CommentEntity,
    val authorName: String,
    val targetLabel: String
)

data class ModerationUiState(
    val pendingComments: List<CommentToModerate> = emptyList(),
    val currentUserId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isProcessing: Boolean = false,
    val showRejectDialog: Boolean = false,
    val selectedCommentId: String? = null,
    val rejectReason: String = ""
)

@HiltViewModel
class ModerationScreenViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val moderationRepository: ModerationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ModerationUiState())
    val state: StateFlow<ModerationUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        _state.update { it.copy(currentUserId = userId) }
        loadPendingComments()
    }

    private fun loadPendingComments() {
        viewModelScope.launch {
            try {
                moderationRepository.observePendingComments().collect { pendingList ->
                    val moderated = pendingList.map { comment ->
                        val author = userRepository.getUser(comment.authorId)
                        val authorName = author?.let { "${it.firstName} ${it.lastName}" } ?: "Аноним"
                        val targetLabel = getTargetLabel(comment.targetType, comment.targetId)
                        CommentToModerate(comment, authorName, targetLabel)
                    }
                    _state.update {
                        it.copy(pendingComments = moderated, isLoading = false)
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    private fun getTargetLabel(targetType: String, targetId: String): String {
        return when (targetType) {
            "club_chat" -> "Чат клуба"
            "level" -> "Ступень ${targetId.take(8)}"
            "honor" -> "Специализация ${targetId.take(8)}"
            "event" -> "Событие ${targetId.take(8)}"
            "task" -> "Задание ${targetId.take(8)}"
            else -> targetType
        }
    }

    fun approveComment(commentId: String) {
        val userId = _state.value.currentUserId ?: return
        _state.update { it.copy(isProcessing = true) }

        viewModelScope.launch {
            try {
                moderationRepository.moderateComment(
                    id = commentId,
                    s = "published",
                    b = userId,
                    at = System.currentTimeMillis(),
                    r = null
                )
                _state.update { it.copy(isProcessing = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isProcessing = false, errorMessage = e.message)
                }
            }
        }
    }

    fun showRejectDialog(commentId: String) {
        _state.update {
            it.copy(showRejectDialog = true, selectedCommentId = commentId, rejectReason = "")
        }
    }

    fun hideRejectDialog() {
        _state.update { it.copy(showRejectDialog = false, selectedCommentId = null, rejectReason = "") }
    }

    fun onRejectReasonChange(reason: String) {
        _state.update { it.copy(rejectReason = reason) }
    }

    fun rejectComment() {
        val commentId = _state.value.selectedCommentId ?: return
        val userId = _state.value.currentUserId ?: return
        val reason = _state.value.rejectReason.trim()

        if (reason.isBlank()) {
            _state.update { it.copy(errorMessage = "Укажите причину отклонения") }
            return
        }

        _state.update { it.copy(isProcessing = true, showRejectDialog = false) }

        viewModelScope.launch {
            try {
                moderationRepository.moderateComment(
                    id = commentId,
                    s = "rejected",
                    b = userId,
                    at = System.currentTimeMillis(),
                    r = reason
                )
                _state.update {
                    it.copy(isProcessing = false, selectedCommentId = null, rejectReason = "")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isProcessing = false, errorMessage = e.message)
                }
            }
        }
    }

    fun hideComment(commentId: String) {
        val userId = _state.value.currentUserId ?: return
        _state.update { it.copy(isProcessing = true) }

        viewModelScope.launch {
            try {
                moderationRepository.moderateComment(
                    id = commentId,
                    s = "hidden",
                    b = userId,
                    at = System.currentTimeMillis(),
                    r = "Скрыто модератором"
                )
                _state.update { it.copy(isProcessing = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isProcessing = false, errorMessage = e.message)
                }
            }
        }
    }
}