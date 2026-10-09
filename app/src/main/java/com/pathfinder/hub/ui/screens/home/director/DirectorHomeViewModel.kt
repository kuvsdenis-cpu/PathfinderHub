package com.pathfinder.hub.ui.screens.home.director

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

data class DirectorHomeUiState(
    val isLoading: Boolean = true,
    val directorName: String? = null,
    val clubName: String? = null,
    val clubLocation: String? = null,
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
    val clubs: List<ClubEntity> = emptyList(),
    val selectedClubId: String? = null,
    val currentUser: UserEntity? = null,
)

@HiltViewModel
class DirectorHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorHomeUiState())
    val state: StateFlow<DirectorHomeUiState> = _state.asStateFlow()

    init {
        observeDirector()
    }

    /**
     * Подписка на user — важно, чтобы при смене активного клуба
     * UI сразу перерисовывал заголовок и вкладки.
     */
    private fun observeDirector() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            userRepository.observeUser(userId).collect { user ->
                if (user == null) return@collect

                // Список клубов: clubIds, fallback [clubId]
                val ids = user.clubIds.ifEmpty {
                    listOfNotNull(user.clubId)
                }
                val activeId = user.clubId ?: ids.firstOrNull()

                // Загружаем сущности клубов
                val clubs = ids.mapNotNull { id ->
                    try { clubRepository.getClub(id) } catch (_: Exception) { null }
                }

                val active = clubs.find { it.id == activeId }

                _state.update {
                    it.copy(
                        isLoading = false,
                        directorName = user.firstName,
                        clubName = active?.name,
                        clubLocation = active?.city,
                        clubs = clubs,
                        selectedClubId = activeId,
                        currentUser = user,
                    )
                }
            }
        }
    }

    /**
     * Смена активного клуба. Обновляет user.clubId, который читают
     * все экраны директора.
     */
    fun switchClub(clubId: String) {
        val userId = sessionManager.getUserId() ?: return
        if (_state.value.selectedClubId == clubId) return
        viewModelScope.launch {
            try {
                userRepository.setActiveClub(userId, clubId)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
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
