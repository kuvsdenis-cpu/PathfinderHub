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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DirectorInvitesUiState(
    val invites: List<InviteEntity> = emptyList(),
    val isLoading: Boolean = false,
    val lastCreated: InviteEntity? = null,
    val errorMessage: String? = null,
    val selectedRole: String = "teen",
    val selectedPermissions: Set<String> = emptySet(),
)

@HiltViewModel
class DirectorInvitesViewModel @Inject constructor(
    private val createInviteUseCase: CreateInviteUseCase,
    private val inviteRepository: InviteRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorInvitesUiState())
    val state: StateFlow<DirectorInvitesUiState> = _state.asStateFlow()

    private companion object {
        val STAFF_ROLES = setOf("director", "instructor", "secretary")
    }

    init {
        observeInvites()
        pullInvites()
    }

    /** Pull инвайтов активного клуба из Firestore при открытии экрана. */
    fun pullInvites() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            val clubId = userRepository.getUser(userId)?.clubId ?: return@launch
            if (clubId.isBlank()) return@launch
            inviteRepository.pullInvites(clubId)
        }
    }

    /**
     * Подписка на инвайты активного клуба (user.clubId).
     * Смена клуба через ClubContextViewModel.switchClub → setActiveClub →
     * user.clubId обновляется → переподписка.
     */
    private fun observeInvites() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            userRepository.observeUser(userId)
                .map { it?.clubId }
                .distinctUntilChanged()
                .collectLatest { clubId ->
                    if (clubId.isNullOrBlank()) {
                        _state.update { it.copy(invites = emptyList()) }
                        return@collectLatest
                    }
                    inviteRepository.observeByClub(clubId).collect { list ->
                        _state.update { it.copy(invites = list) }
                    }
                }
        }
    }

    fun onRoleSelected(role: String) {
        _state.update {
            it.copy(selectedRole = role, selectedPermissions = emptySet())
        }
    }

    fun onPermissionToggle(perm: String) {
        _state.update {
            val cur = it.selectedPermissions
            val next = if (perm in cur) cur - perm else cur + perm
            it.copy(selectedPermissions = next)
        }
    }

    fun createInvite() {
        val s = _state.value
        val role = s.selectedRole
        _state.update { it.copy(isLoading = true, errorMessage = null, lastCreated = null) }

        viewModelScope.launch {
            val userId = sessionManager.getUserId()
            if (userId == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
                return@launch
            }
            val clubId = userRepository.getUser(userId)?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не выбран") }
                return@launch
            }
            val permissions = if (role in STAFF_ROLES) s.selectedPermissions.toList() else emptyList()
            createInviteUseCase(role = role, clubId = clubId, permissions = permissions).fold(
                onSuccess = { invite ->
                    _state.update { it.copy(isLoading = false, lastCreated = invite) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Ошибка создания инвайта")
                    }
                },
            )
        }
    }

    fun resendInvite(source: InviteEntity) {
        _state.update { it.copy(isLoading = true, errorMessage = null, lastCreated = null) }
        viewModelScope.launch {
            inviteRepository.resendInvite(source).fold(
                onSuccess = { fresh ->
                    _state.update { it.copy(isLoading = false, lastCreated = fresh) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка повторной отправки")
                    }
                },
            )
        }
    }

    fun clearLastCreated() {
        _state.update { it.copy(lastCreated = null) }
    }
}
