package com.pathfinder.hub.ui.chat.list

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.ChatRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.model.DirectMessageMeta
import com.pathfinder.hub.domain.model.GroupChatMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DmListItem(
    val dm: DirectMessageMeta,
    val otherUserId: String,
    val otherName: String,
    val otherInitials: String,
)

data class GroupListItem(
    val group: GroupChatMeta,
    val participantsCount: Int,
)

data class ChatsListUiState(
    val isLoading: Boolean = true,
    val clubId: String? = null,
    val currentUserId: String? = null,
    val userRole: String = "",
    val dms: List<DmListItem> = emptyList(),
    val groups: List<GroupListItem> = emptyList(),
    val errorMessage: String? = null,
) {
    val canCreateGroup: Boolean
        get() = userRole in listOf("director", "instructor", "conference_admin")
}

@HiltViewModel
class ChatsListViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val chatRepo: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatsListUiState())
    val state: StateFlow<ChatsListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = sessionManager.getUserId()
            if (uid.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
                return@launch
            }
            val user = userRepository.getUser(uid)
            val clubId = user?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            _state.update {
                it.copy(clubId = clubId, currentUserId = uid, userRole = user.role)
            }

            launch {
                chatRepo.observeMyDMs(clubId, uid)
                    .catch { e -> Log.e(TAG, "myDMs error", e) }
                    .collect { list ->
                        val enriched = list.map { dm ->
                            val other = chatRepo.otherParticipant(dm, uid)
                            val u = if (other.isNotBlank()) userRepository.getUser(other) else null
                            val initials = u?.let {
                                "${it.firstName.firstOrNull()?.uppercase() ?: ""}${it.lastName.firstOrNull()?.uppercase() ?: ""}"
                            } ?: "?"
                            DmListItem(
                                dm = dm,
                                otherUserId = other,
                                otherName = u?.let { "${it.firstName} ${it.lastName}" } ?: "Собеседник",
                                otherInitials = initials,
                            )
                        }.sortedByDescending { it.dm.lastMessageAt ?: it.dm.createdAt }
                        _state.update { it.copy(isLoading = false, dms = enriched) }
                    }
            }

            launch {
                chatRepo.observeMyGroups(clubId, uid)
                    .catch { e -> Log.e(TAG, "myGroups error", e) }
                    .collect { list ->
                        val enriched = list.map { g ->
                            GroupListItem(group = g, participantsCount = g.participants.size)
                        }.sortedByDescending { it.group.lastMessageAt ?: it.group.createdAt }
                        _state.update { it.copy(groups = enriched) }
                    }
            }
        }
    }

    companion object { private const val TAG = "ChatsListVM" }
}
