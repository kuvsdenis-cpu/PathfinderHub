package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClubSettingsUiState(
    val isLoading: Boolean = true,
    val club: ClubEntity? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class ClubSettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ClubSettingsUiState())
    val state: StateFlow<ClubSettingsUiState> = _state.asStateFlow()

    init {
        loadClub()
    }

    private fun loadClub() {
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
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }

                clubRepository.observeClub(clubId).collect { club ->
                    _state.update { it.copy(isLoading = false, club = club) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки") }
            }
        }
    }

    fun updateClub(updatedClub: ClubEntity) {
        _state.update { it.copy(isSaving = true, errorMessage = null, saveSuccess = false) }

        viewModelScope.launch {
            try {
                clubRepository.update(updatedClub)
                _state.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка сохранения") }
            }
        }
    }

    fun clearSaveSuccess() {
        _state.update { it.copy(saveSuccess = false) }
    }
}