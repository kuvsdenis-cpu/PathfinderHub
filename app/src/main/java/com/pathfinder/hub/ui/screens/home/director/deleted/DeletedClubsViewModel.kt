package com.pathfinder.hub.ui.screens.home.director.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.domain.usecase.club.RestoreClubUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeletedClubsUiState(
    val isLoading: Boolean = true,
    val clubs: List<ClubEntity> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class DeletedClubsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val clubRepository: ClubRepository,
    private val restoreClub: RestoreClubUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DeletedClubsUiState())
    val state: StateFlow<DeletedClubsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            clubRepository.observeDeletedClubs().collect { list ->
                _state.update { it.copy(isLoading = false, clubs = list) }
            }
        }
    }

    fun restore(clubId: String, clubName: String) {
        val uid = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            val result = restoreClub(
                clubId = clubId,
                clubName = clubName,
                userId = uid,
                userRole = "director",
            )
            if (result.isFailure) {
                _state.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }
}
