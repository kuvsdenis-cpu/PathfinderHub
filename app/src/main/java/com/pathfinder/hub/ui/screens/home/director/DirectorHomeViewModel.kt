package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.ClubRepository
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
    val errorMessage: String? = null
)

@HiltViewModel
class DirectorHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorHomeUiState())
    val state: StateFlow<DirectorHomeUiState> = _state.asStateFlow()

    init {
        loadDirectorInfo()
    }

    private fun loadDirectorInfo() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            try {
                val user = userRepository.getUser(userId)
                if (user != null && user.clubId != null) {
                    val club = clubRepository.getClub(user.clubId)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            directorName = user.firstName,
                            clubName = club?.name,
                            clubLocation = club?.city
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}