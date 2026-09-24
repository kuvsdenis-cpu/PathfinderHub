package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import com.pathfinder.hub.data.repository.InviteRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.usecase.director.CreateInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DirectorInvitesUiState(
    val invites: List<InviteEntity> = emptyList(),
    val isLoading: Boolean = false,
    val lastCreated: InviteEntity? = null,
    val errorMessage: String? = null,
    val selectedRole: String = "teen"
)

@HiltViewModel
class DirectorInvitesViewModel @Inject constructor(
    private val createInviteUseCase: CreateInviteUseCase,
    private val inviteRepository: InviteRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorInvitesUiState())
    val state: StateFlow<DirectorInvitesUiState> = _state.asStateFlow()

    init {
        observeInvites()
    }

    private fun observeInvites() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            val user = userRepository.getUser(userId) ?: return@launch
            val clubId = user.clubId ?: return@launch
            inviteRepository.observeByClub(clubId).collect { list ->
                _state.update { it.copy(invites = list) }
            }
        }
    }

    fun onRoleSelected(role: String) {
        _state.update { it.copy(selectedRole = role) }
    }

    fun createInvite() {
        _state.update { it.copy(isLoading = true, errorMessage = null, lastCreated = null) }

        viewModelScope.launch {
            createInviteUseCase(_state.value.selectedRole).fold(
                onSuccess = { invite ->
                    _state.update { it.copy(isLoading = false, lastCreated = invite) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Ошибка создания инвайта"
                        )
                    }
                }
            )
        }
    }

    fun clearLastCreated() {
        _state.update { it.copy(lastCreated = null) }
    }
}