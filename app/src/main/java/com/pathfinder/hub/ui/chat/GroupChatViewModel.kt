package com.pathfinder.hub.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupChatUiState(
    val targetType: String = "",
    val targetId: String = "",
    val groupName: String = "Групповой чат",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GroupChatUiState())
    val state: StateFlow<GroupChatUiState> = _state.asStateFlow()

    fun init(targetType: String, targetId: String) {
        if (_state.value.targetId == targetId && _state.value.targetType == targetType) return

        _state.update {
            it.copy(targetType = targetType, targetId = targetId, isLoading = true)
        }

        viewModelScope.launch {
            try {
                val groupName = when (targetType) {
                    "level" -> {
                        val level = levelRepository.getLevel(targetId)
                        level?.name ?: "Ступень"
                    }
                    "honor" -> {
                        val honor = honorRepository.getHonor(targetId)
                        honor?.name ?: "Специализация"
                    }
                    else -> "Групповой чат"
                }

                _state.update {
                    it.copy(groupName = groupName, isLoading = false)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }
}