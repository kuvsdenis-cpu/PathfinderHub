package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClubSettingsUiState(
    val isLoading: Boolean = true,
    val club: ClubEntity? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false,
    val currentUserIsOwner: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
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
            // Отдельная подписка: обновляем только флаг owner,
            // не трогая существующую цепочку загрузки клуба.
            launch {
                userRepository.observeUser(userId).collect { u ->
                    _state.update { it.copy(currentUserIsOwner = u?.isOwnerClub == true) }
                }
            }

            userRepository.observeUser(userId)
                .map { it?.clubId }
                .distinctUntilChanged()
                .flatMapLatest { clubId ->
                    if (clubId.isNullOrBlank()) flowOf(null)
                    else clubRepository.observeClub(clubId)
                }
                .collect { club ->
                    _state.update { it.copy(isLoading = false, club = club) }
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

    /** Soft delete текущего клуба. Только owner. */
    fun deleteClub(onDeleted: () -> Unit = {}) {
        val userId = sessionManager.getUserId() ?: return
        val club = _state.value.club ?: return
        viewModelScope.launch {
            try {
                clubRepository.softDelete(club.id, userId)
                onDeleted()
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка удаления") }
            }
        }
    }
}
