package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.InviteRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.permissions.PermissionRegistry
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
    val clubs: List<ClubEntity> = emptyList(),
    val selectedClubId: String? = null,
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
    private val clubRepository: ClubRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorInvitesUiState())
    val state: StateFlow<DirectorInvitesUiState> = _state.asStateFlow()

    init {
        observeDirector()
        observeInvites()
    }

    private fun observeDirector() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            userRepository.observeUser(userId).collect { user ->
                if (user == null) return@collect
                val ids = user.clubIds.ifEmpty { listOfNotNull(user.clubId) }
                val clubs = ids.mapNotNull { id ->
                    try { clubRepository.getClub(id) } catch (_: Exception) { null }
                }
                val activeId = user.clubId ?: clubs.firstOrNull()?.id
                _state.update {
                    it.copy(
                        clubs = clubs,
                        selectedClubId = it.selectedClubId ?: activeId,
                    )
                }
            }
        }
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
        _state.update {
            it.copy(
                selectedRole = role,
                // При выборе "director" — предзаполняем безопасным набором
                selectedPermissions = if (role == "director")
                    PermissionRegistry.DEFAULT_CO_DIRECTOR.toSet()
                else emptySet(),
            )
        }
    }

    fun onClubSelected(clubId: String) {
        _state.update { it.copy(selectedClubId = clubId) }
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
        val clubId = s.selectedClubId

        if (role == "director" && clubId == null) {
            _state.update { it.copy(errorMessage = "Не выбран клуб") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null, lastCreated = null) }

        viewModelScope.launch {
            val permissions = if (role == "director") s.selectedPermissions.toList() else emptyList()
            createInviteUseCase(role = role, clubId = clubId, permissions = permissions).fold(
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
