package com.pathfinder.hub.ui.screens.home.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.AuthRepository
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.FcmTokenRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeHeaderUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val clubId: String? = null,
    val clubName: String? = null,
    val clubLocation: String? = null,
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
)

/**
 * Общий VM для "домашних" экранов ролей (instructor / secretary / parent).
 * Даёт user + клуб + logout.
 */
@HiltViewModel
class HomeHeaderViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeHeaderUiState())
    val state: StateFlow<HomeHeaderUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
        } else {
            viewModelScope.launch {
                userRepository.observeUser(uid).collect { u ->
                    val club = u?.clubId?.let {
                        try { clubRepository.getClub(it) } catch (_: Exception) { null }
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            user = u,
                            clubId = u?.clubId,
                            clubName = club?.name,
                            clubLocation = club?.city,
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        val uid = sessionManager.getUserId()
        sessionManager.clear()
        _state.update { it.copy(loggedOut = true) }

        viewModelScope.launch {
            try { authRepository.signOut() } catch (_: Exception) {}
            if (uid != null) {
                try { fcmTokenRepository.removeCurrentToken(uid) } catch (_: Exception) {}
            }
        }
    }
}
