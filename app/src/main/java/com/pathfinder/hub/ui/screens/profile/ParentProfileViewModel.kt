package com.pathfinder.hub.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.AuthRepository
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParentProfileUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val clubName: String? = null,
    val directorName: String? = null,
    val directorEmail: String? = null,
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class ParentProfileViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentProfileUiState())
    val state: StateFlow<ParentProfileUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            observeUser(uid)
        }
    }

    private fun observeUser(uid: String) {
        viewModelScope.launch {
            userRepository.observeUser(uid).collect { u ->
                _state.update { it.copy(user = u, isLoading = false) }
                val clubId = u?.clubId ?: u?.clubIds?.firstOrNull()
                if (!clubId.isNullOrBlank() && _state.value.clubName == null) {
                    loadClubAndDirector(clubId)
                }
            }
        }
    }

    private suspend fun loadClubAndDirector(clubId: String) {
        try {
            val club = clubRepository.getClub(clubId)
            _state.update { it.copy(clubName = club?.name) }
        } catch (_: Exception) {}
        try {
            val director = userRepository.getClubOwner(clubId)
            _state.update {
                it.copy(
                    directorName = director?.let { d ->
                        "${d.firstName} ${d.lastName}".trim().ifBlank { "Директор клуба" }
                    },
                    directorEmail = director?.email,
                )
            }
        } catch (_: Exception) {}
    }

    fun logout() {
        sessionManager.clear()
        _state.update { it.copy(loggedOut = true) }
        viewModelScope.launch {
            try { authRepository.signOut() } catch (_: Exception) {}
        }
    }
}
