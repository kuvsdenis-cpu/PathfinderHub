package com.pathfinder.hub.ui.chat.group

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.ChatRepository
import com.pathfinder.hub.data.repository.CustomGroupRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class GroupMessageUi(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorInitials: String,
    val text: String,
    val attachments: List<String>,
    val createdAt: Date,
    val isOwn: Boolean,
    val editedAt: Date? = null,
)

data class CustomGroupChatUiState(
    val isLoading: Boolean = true,
    val clubId: String? = null,
    val groupId: String? = null,
    val currentUserId: String? = null,
    val groupName: String = "Группа",
    val messages: List<GroupMessageUi> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val isLeaving: Boolean = false,
    val leaveBlockedReason: String? = null,
    val editingMessageId: String? = null,
    val editDraft: String = "",
    val errorMessage: String? = null,
)

@HiltViewModel
class CustomGroupChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val chatRepo: ChatRepository,
    private val customGroupRepo: CustomGroupRepository,
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    private val _state = MutableStateFlow(CustomGroupChatUiState())
    val state: StateFlow<CustomGroupChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = sessionManager.getUserId()
            if (uid.isNullOrBlank() || groupId.isBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Нет данных чата") }
                return@launch
            }
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            _state.update {
                it.copy(clubId = clubId, groupId = groupId, currentUserId = uid)
            }

            launch {
                chatRepo.observeGroupMeta(clubId, groupId)
                    .catch { e -> Log.e(TAG, "group meta error", e) }
                    .collect { meta ->
                        if (meta != null) _state.update { it.copy(groupName = meta.name) }
                    }
            }

            launch {
                chatRepo.observeGroupMessages(clubId, groupId)
                    .catch { e ->
                        Log.e(TAG, "group messages error", e)
                        _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                    }
                    .collect { raw ->
                        // Разрешаем имена авторов
                        val ui = raw.map { m ->
                            val author = userRepository.getUser(m.authorId)
                            val name = author?.let { "${it.firstName} ${it.lastName}" } ?: "Аноним"
                            val initials = author?.let {
                                "${it.firstName.firstOrNull()?.uppercase() ?: ""}${it.lastName.firstOrNull()?.uppercase() ?: ""}"
                            } ?: "?"
                            GroupMessageUi(
                                id = m.id,
                                authorId = m.authorId,
                                authorName = name,
                                authorInitials = initials,
                                text = m.text,
                                attachments = m.attachments,
                                createdAt = m.createdAt,
                                isOwn = m.authorId == uid,
                                editedAt = m.editedAt,
                            )
                        }
                        _state.update { it.copy(messages = ui, isLoading = false) }
                    }
            }
        }
    }

    fun onInputChange(v: String) = _state.update { it.copy(inputText = v) }

    fun send() {
        val s = _state.value
        val text = s.inputText.trim()
        if (text.isBlank()) return
        val clubId = s.clubId ?: return
        val gid = s.groupId ?: return
        val uid = s.currentUserId ?: return

        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            val r = chatRepo.sendGroupMessage(clubId, gid, uid, text)
            r.fold(
                onSuccess = { _state.update { it.copy(inputText = "", isSending = false) } },
                onFailure = { e ->
                    _state.update { it.copy(isSending = false, errorMessage = e.message) }
                },
            )
        }
    }

    fun leaveGroup(onLeft: () -> Unit) {
        val s = _state.value
        val gid = s.groupId ?: return
        val uid = s.currentUserId ?: return
        if (s.isLeaving) return
        _state.update { it.copy(isLeaving = true, leaveBlockedReason = null) }
        viewModelScope.launch {
            val r = customGroupRepo.leaveGroup(gid, uid)
            r.fold(
                onSuccess = {
                    _state.update { it.copy(isLeaving = false) }
                    onLeft()
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLeaving = false, leaveBlockedReason = e.message ?: "Не удалось выйти")
                    }
                },
            )
        }
    }

    fun clearLeaveError() = _state.update { it.copy(leaveBlockedReason = null) }

    fun startEdit(message: GroupMessageUi) {
        if (message.authorId != _state.value.currentUserId) return
        _state.update { it.copy(editingMessageId = message.id, editDraft = message.text) }
    }

    fun cancelEdit() = _state.update { it.copy(editingMessageId = null, editDraft = "") }

    fun onEditDraftChange(v: String) = _state.update { it.copy(editDraft = v) }

    fun submitEdit() {
        val s = _state.value
        val clubId = s.clubId ?: return
        val gid = s.groupId ?: return
        val uid = s.currentUserId ?: return
        val mid = s.editingMessageId ?: return
        val text = s.editDraft.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            val r = chatRepo.editGroupMessage(clubId, gid, mid, uid, text)
            r.fold(
                onSuccess = { _state.update { it.copy(editingMessageId = null, editDraft = "") } },
                onFailure = { e -> _state.update { it.copy(errorMessage = e.message) } },
            )
        }
    }

    companion object { private const val TAG = "CustomGroupChatVM" }
}
