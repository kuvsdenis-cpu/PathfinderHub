package com.pathfinder.hub.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DigitalUniformUiState(
    val isLoading: Boolean = true,
    val uniform: DigitalUniformEntity? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class DigitalUniformViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val achievementRepository: AchievementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DigitalUniformUiState())
    val state: StateFlow<DigitalUniformUiState> = _state.asStateFlow()

    init {
        loadUniform()
    }

    private fun loadUniform() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            achievementRepository.observeUniform(userId).collect { uniform ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        uniform = uniform
                    )
                }
            }
        }
    }
}