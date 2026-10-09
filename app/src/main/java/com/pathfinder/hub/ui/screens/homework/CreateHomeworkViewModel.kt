package com.pathfinder.hub.ui.screens.homework

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class CreateHomeworkUiState(
    val title: String = "",
    val description: String = "",
    val dueDate: Date? = null,
    val reward: String = "",
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
    val errorMessage: String? = null,
    val teenCount: Int = 0,
)

@HiltViewModel
class CreateHomeworkViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateHomeworkUiState())
    val state: StateFlow<CreateHomeworkUiState> = _state.asStateFlow()

    private val creatorId: String? = sessionManager.getUserId()

    init {
        val uid = creatorId
        if (uid != null) {
            viewModelScope.launch {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId ?: return@launch
                try {
                    val teens = userRepository.observeClubMembersByRole(clubId, "teen").first()
                    _state.update { it.copy(teenCount = teens.size) }
                } catch (_: Exception) {}
            }
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onDueDateChange(v: Date) = _state.update { it.copy(dueDate = v) }
    fun onRewardChange(v: String) = _state.update { it.copy(reward = v) }

    fun create() {
        val s = _state.value
        val uid = creatorId
        if (uid == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        if (s.dueDate == null) {
            _state.update { it.copy(errorMessage = "Укажите срок") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                val teens = userRepository.observeClubMembersByRole(clubId, "teen").first()
                if (teens.isEmpty()) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "В клубе нет следопытов")
                    }
                    return@launch
                }
                teens.forEach { teen ->
                    achievementRepository.upsertHomework(
                        HomeworkEntity(
                            id = UUID.randomUUID().toString(),
                            clubId = clubId,
                            childId = teen.id,
                            instructorId = uid,
                            title = s.title.trim(),
                            description = s.description.trim(),
                            dueDate = s.dueDate,
                            reward = s.reward.trim().ifBlank { null },
                            status = "assigned",
                            createdAt = Date(),
                            approvedAt = null,
                            pendingSync = true,
                        )
                    )
                }
                _state.update { it.copy(isLoading = false, isCreated = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка создания")
                }
            }
        }
    }
}
