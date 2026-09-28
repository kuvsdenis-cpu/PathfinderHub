package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClubMembersUiState(
    val isLoading: Boolean = true,
    val members: List<UserEntity> = emptyList(),
    val currentUserId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ClubMembersViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ClubMembersUiState())
    val state: StateFlow<ClubMembersUiState> = _state.asStateFlow()

    init {
        loadMembers()
    }

    private fun loadMembers() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не авторизован") }
            return
        }

        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId

                if (clubId.isNullOrBlank()) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "Клуб не найден")
                    }
                    return@launch
                }

                _state.update { it.copy(isLoading = false, currentUserId = userId) }

                // Подписываемся на реактивный список участников клуба
                userRepository.observeClubMembers(clubId).collect { members ->
                    _state.update { it.copy(members = members) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }
}