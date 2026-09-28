package com.pathfinder.hub.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoleRouterUiState(
    val isLoading: Boolean = true,
    val role: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class RoleRouterViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RoleRouterUiState())
    val state: StateFlow<RoleRouterUiState> = _state.asStateFlow()

    init {
        loadUserRole()
    }

    private fun loadUserRole() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            try {
                val user = userRepository.getUser(userId)
                _state.update {
                    it.copy(
                        isLoading = false,
                        role = user?.role
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}