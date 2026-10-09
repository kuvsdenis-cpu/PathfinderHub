package com.pathfinder.hub.ui.screens.home.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.parent.ParentMessageEntity
import com.pathfinder.hub.data.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ParentMessagesUiState(
    val isLoading: Boolean = true,
    val messages: List<ParentMessageEntity> = emptyList(),
    val replyTarget: ParentMessageEntity? = null,
    val replyText: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class ParentMessagesViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentMessagesUiState())
    val state: StateFlow<ParentMessagesUiState> = _state.asStateFlow()

    private val parentId: String? = sessionManager.getUserId()

    init {
        val uid = parentId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            viewModelScope.launch {
                parentRepository.observeMessages(uid).collect { list ->
                    _state.update { it.copy(isLoading = false, messages = list) }
                }
            }
        }
    }

    fun openReply(m: ParentMessageEntity) {
        _state.update { it.copy(replyTarget = m, replyText = m.response ?: "") }
    }

    fun closeReply() {
        _state.update { it.copy(replyTarget = null, replyText = "") }
    }

    fun onReplyChange(text: String) {
        _state.update { it.copy(replyText = text) }
    }

    fun sendReply() {
        val m = _state.value.replyTarget ?: return
        val text = _state.value.replyText.trim()
        if (text.isBlank()) return

        _state.update { it.copy(isSending = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                parentRepository.upsertMessage(
                    m.copy(
                        response = text,
                        respondedAt = Date(),
                        pendingSync = true,
                    )
                )
                _state.update {
                    it.copy(
                        isSending = false,
                        replyTarget = null,
                        replyText = "",
                        infoMessage = "Ответ отправлен",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSending = false, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
