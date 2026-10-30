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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val hasDeletedClubs: Boolean = false,
    val noClubs: Boolean = false,
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
        // Timeout: если через 5 сек состояние не готово — снять spinner
        viewModelScope.launch {
            kotlinx.coroutines.delay(5_000)
            if (_state.value.isLoading) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Синхронизация не завершилась. Проверьте сеть и обновите.",
                    )
                }
            }
        }
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
            // Подписка на корзину (только для owner)
            launch {
                clubRepository.observeDeletedClubs().collect { deleted ->
                    _state.update { it.copy(hasDeletedClubs = deleted.isNotEmpty()) }
                }
            }

            userRepository.observeUser(userId)
                // #7: реагируем только на то, что реально влияет на UI
                .distinctUntilChanged { old, new ->
                    old?.clubId == new?.clubId &&
                    old?.clubIds == new?.clubIds &&
                    old?.firstName == new?.firstName &&
                    old?.directorPermissions == new?.directorPermissions &&
                    old?.isOwnerClub == new?.isOwnerClub
                }
                .collect { user ->
                    if (user == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Пользователь не найден локально. Проверьте сеть.",
                            )
                        }
                        return@collect
                    }

                    // Список клубов: clubIds, fallback [clubId]
                    val ids = user.clubIds.ifEmpty {
                        listOfNotNull(user.clubId)
                    }
                    val activeId = user.clubId ?: ids.firstOrNull()

                    // #7: один запрос getClubs(ids) вместо N × getClub(id)
                    val clubs = try {
                        clubRepository.getClubs(ids).filter { it.deletedAt == null }
                    } catch (_: Exception) { emptyList() }

                    var active = clubs.find { it.id == activeId }
                    // Fallback: активный клуб не найден (например, demo_club без Firestore) —
                    // авто-переключение на первый доступный
                    if (active == null && clubs.isNotEmpty()) {
                        val first = clubs.first()
                        active = first
                        viewModelScope.launch {
                            try { userRepository.setActiveClub(userId, first.id) } catch (_: Exception) {}
                        }
                    }

                    _state.update {
                        it.copy(
                            isLoading = false,
                            directorName = user.firstName,
                            clubName = active?.name,
                            clubLocation = active?.city,
                            clubs = clubs,
                            selectedClubId = active?.id ?: activeId,
                            currentUser = user,
                            noClubs = user.clubIds.isEmpty() && user.clubId == null,
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
