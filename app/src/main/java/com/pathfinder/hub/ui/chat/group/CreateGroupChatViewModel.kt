package com.pathfinder.hub.ui.chat.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.ChatRepository
import com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity
import com.pathfinder.hub.data.repository.CustomGroupRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupChatUiState(
    val isLoading: Boolean = true,
    val clubId: String? = null,
    val currentUserId: String? = null,
    val name: String = "",
    val members: List<UserEntity> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isCreating: Boolean = false,
    val createdGroupId: String? = null,
    val errorMessage: String? = null,
    val customGroups: List<CustomGroupEntity> = emptyList(),
) {
    val canCreate: Boolean
        get() = name.isNotBlank() && selectedIds.isNotEmpty() && !isCreating
}

@HiltViewModel
class CreateGroupChatViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val chatRepo: ChatRepository,
    private val customGroupRepository: CustomGroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateGroupChatUiState())
    val state: StateFlow<CreateGroupChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = sessionManager.getUserId()
            if (uid.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
                return@launch
            }
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            _state.update { it.copy(clubId = clubId, currentUserId = uid) }

            launch {
                userRepository.observeClubMembers(clubId).collect { list ->
                    val filtered = list
                        .filter { it.id != uid && it.status != "deleted" && it.role != "parent" }
                        .sortedBy { "${it.firstName} ${it.lastName}" }
                    _state.update { it.copy(isLoading = false, members = filtered) }
                }
            }
            launch {
                customGroupRepository.observeActiveByClub(clubId).collect { groups ->
                    _state.update { it.copy(customGroups = groups) }
                }
            }
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v) }

    fun toggleMember(userId: String) {
        _state.update { s ->
            val set = s.selectedIds.toMutableSet()
            if (set.contains(userId)) set.remove(userId) else set.add(userId)
            s.copy(selectedIds = set)
        }
    }

    /**
     * B5.1: заполнить selectedIds участниками пользовательской группы.
     * Фильтр: берём только тех, кто уже есть в state.members
     * (т.е. не родители, не удалённые, не сам создатель).
     */
    fun fillFromGroup(groupId: String) {
        viewModelScope.launch {
            try {
                val ids = customGroupRepository.resolveMembers(groupId)
                val membersSet = _state.value.members.map { it.id }.toSet()
                val valid = ids.filter { it in membersSet }
                _state.update { it.copy(selectedIds = valid.toSet()) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearMembers() = _state.update { it.copy(selectedIds = emptySet()) }

    fun create() {
        val s = _state.value
        val clubId = s.clubId ?: return
        val uid = s.currentUserId ?: return
        if (!s.canCreate) return

        _state.update { it.copy(isCreating = true, errorMessage = null) }
        viewModelScope.launch {
            val r = chatRepo.createGroup(clubId, uid, s.name, s.selectedIds.toList())
            r.fold(
                onSuccess = { groupId ->
                    _state.update { it.copy(isCreating = false, createdGroupId = groupId) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isCreating = false, errorMessage = e.message) }
                },
            )
        }
    }

    fun clearCreated() = _state.update { it.copy(createdGroupId = null) }
    fun clearError() = _state.update { it.copy(errorMessage = null) }
}
