package com.pathfinder.hub.ui.screens.club_info

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.repository.ClubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClubInfoUiState(
    val isLoading: Boolean = true,
    val club: ClubEntity? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ClubInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clubRepository: ClubRepository,
) : ViewModel() {

    private val clubId: String = savedStateHandle.get<String>("clubId") ?: ""

    private val _state = MutableStateFlow(ClubInfoUiState())
    val state: StateFlow<ClubInfoUiState> = _state.asStateFlow()

    init {
        if (clubId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Клуб не указан") }
        } else {
            load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val club = clubRepository.getClub(clubId)
                if (club == null) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                } else {
                    _state.update { it.copy(isLoading = false, club = club) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }
}
