package com.pathfinder.hub.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
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
    val clubId: String? = null,
    val groupName: String = "Групповой чат",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "GroupChatVM"
    }

    private val _state = MutableStateFlow(GroupChatUiState())
    val state: StateFlow<GroupChatUiState> = _state.asStateFlow()

    fun init(targetType: String, targetId: String) {
        if (_state.value.targetId == targetId && _state.value.targetType == targetType) return

        _state.update {
            it.copy(targetType = targetType, targetId = targetId, isLoading = true)
        }

        viewModelScope.launch {
            try {
                // 1. Получаем clubId текущего пользователя
                val userId = sessionManager.getUserId()
                val currentUser = userId?.let { userRepository.getUser(it) }
                val clubId = currentUser?.clubId

                if (clubId.isNullOrBlank()) {
                    Log.w(TAG, "clubId пустой — групповой чат не сможет писать в Firestore")
                }

                // 2. Название группы в зависимости от targetType
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
                    it.copy(
                        clubId = clubId,
                        groupName = groupName,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "init failed", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }
}