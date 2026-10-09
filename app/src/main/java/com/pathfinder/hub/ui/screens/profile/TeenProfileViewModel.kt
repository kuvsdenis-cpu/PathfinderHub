package com.pathfinder.hub.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.AuthRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.FcmTokenRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TeenProfileUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val completedLevels: Int = 0,
    val achievements: Int = 0,
    val loggedOut: Boolean = false,   // <-- ДОБАВЛЕНО
)

@HiltViewModel
class TeenProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val achievementRepository: AchievementRepository,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TeenProfileUiState())
    val state: StateFlow<TeenProfileUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                userRepository.observeUser(userId).collect { u ->
                    _state.update { it.copy(user = u) }
                }
            }
            viewModelScope.launch {
                levelRepository.observeCompletions(userId).collect { lc ->
                    _state.update {
                        it.copy(completedLevels = lc.size, isLoading = false)
                    }
                }
            }
            viewModelScope.launch {
                achievementRepository.observeEarned(userId).collect { a ->
                    _state.update { it.copy(achievements = a.size) }
                }
            }
        }
    }

    /**
     * Выход: Firebase Auth + очистка локальной сессии.
     * После успеха ставим флаг loggedOut — UI отреагирует и навигирует на LOGIN.
     */
    fun logout() {
        // Сначала — синхронно, без сети:
        val uid = sessionManager.getUserId()
        sessionManager.clear()
        _state.update { it.copy(loggedOut = true) }

        // Потом — в фоне, не блокирует UI:
        viewModelScope.launch {
            try { authRepository.signOut() } catch (_: Exception) {}
            if (uid != null) {
                try { fcmTokenRepository.removeCurrentToken(uid) } catch (_: Exception) {}
            }
        }
    }
}