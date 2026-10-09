package com.pathfinder.hub.ui.chat.dm

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.ChatRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DMChatUiState(
    val isLoading: Boolean = true,
    val clubId: String? = null,
    val dmId: String? = null,
    val currentUserId: String? = null,
    val otherUserId: String = "",
    val otherName: String = "Собеседник",
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class DMChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val chatRepo: ChatRepository,
) : ViewModel() {

    private val dmId: String = savedStateHandle.get<String>("dmId") ?: ""
    private val otherUid: String = savedStateHandle.get<String>("otherUid") ?: ""

    private val _state = MutableStateFlow(DMChatUiState())
    val state: StateFlow<DMChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = sessionManager.getUserId()
            if (uid.isNullOrBlank() || dmId.isBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Нет данных чата") }
                return@launch
            }
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }

            val other = if (otherUid.isNotBlank()) userRepository.getUser(otherUid) else null
            val otherName = other?.let { "${it.firstName} ${it.lastName}" } ?: "Собеседник"

            _state.update {
                it.copy(
                    clubId = clubId,
                    dmId = dmId,
                    currentUserId = uid,
                    otherUserId = otherUid,
                    otherName = otherName,
                )
            }

            chatRepo.observeDmMessages(clubId, dmId)
                .catch { e ->
                    Log.e(TAG, "observeMessages error", e)
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
                .collect { list ->
                    _state.update { it.copy(messages = list, isLoading = false) }
                }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun send() {
        val s = _state.value
        val text = s.inputText.trim()
        if (text.isBlank()) return
        val clubId = s.clubId ?: return
        val dm = s.dmId ?: return
        val uid = s.currentUserId ?: return

        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            val r = chatRepo.sendDmMessage(clubId, dm, uid, text)
            r.fold(
                onSuccess = { _state.update { it.copy(inputText = "", isSending = false) } },
                onFailure = { e ->
                    _state.update { it.copy(isSending = false, errorMessage = e.message) }
                },
            )
        }
    }

    companion object { private const val TAG = "DMChatVM" }
}
