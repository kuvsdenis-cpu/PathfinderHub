package com.pathfinder.hub.ui.screens.home.conference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.ClubEntity
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

data class ConferenceStaffUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val conferenceName: String? = null,
    val clubs: List<ClubEntity> = emptyList(),
    val members: List<UserEntity> = emptyList(),
    val isAdmin: Boolean = false,
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
)

/**
 * Общий VM для conference_admin и conference_secretary.
 * Показывает клубы конференции и её кадры.
 */
@HiltViewModel
class ConferenceStaffViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConferenceStaffUiState())
    val state: StateFlow<ConferenceStaffUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(uid: String) {
        viewModelScope.launch {
            userRepository.observeUser(uid).collect { u ->
                if (u == null) return@collect
                val confId = u.conferenceId
                if (confId.isNullOrBlank()) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "Конференция не найдена")
                    }
                    return@collect
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        user = u,
                        isAdmin = u.role == "conference_admin",
                    )
                }

                // Подписки
                launch {
                    clubRepository.observeClubsByConference(confId).collect { list ->
                        _state.update { it.copy(clubs = list) }
                    }
                }
                launch {
                    userRepository.observeConferenceMembers(confId).collect { list ->
                        _state.update { it.copy(members = list) }
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
