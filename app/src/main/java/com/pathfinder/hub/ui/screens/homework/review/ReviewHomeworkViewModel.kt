package com.pathfinder.hub.ui.screens.homework.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewHomeworkUiState(
    val isLoading: Boolean = true,
    val items: List<HomeworkEntity> = emptyList(),
    val childNames: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ReviewHomeworkViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewHomeworkUiState())
    val state: StateFlow<ReviewHomeworkUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(userId: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.getUser(userId)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                achievementRepository.observePendingHomeworkByClub(clubId).collect { list ->
                    // Подтягиваем имена детей
                    val names = mutableMapOf<String, String>()
                    list.map { it.childId }.distinct().forEach { childId ->
                        val child = userRepository.getUser(childId)
                        if (child != null) {
                            names[childId] = "${child.firstName} ${child.lastName}".trim()
                        }
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            items = list,
                            childNames = names,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun approve(hw: HomeworkEntity) {
        viewModelScope.launch {
            try {
                achievementRepository.approveHomework(hw)
                _state.update { it.copy(message = "Одобрено: " + hw.title) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun reject(hw: HomeworkEntity) {
        viewModelScope.launch {
            try {
                achievementRepository.rejectHomework(hw)
                _state.update { it.copy(message = "Возвращено: " + hw.title) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(message = null, errorMessage = null) }
}
