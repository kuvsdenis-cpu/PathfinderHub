package com.pathfinder.hub.ui.screens.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class CreateHomeworkUiState(
    val title: String = "",
    val description: String = "",
    val dueDate: Date? = null,
    val rewardType: String = "text",
    val rewardText: String = "",
    val rewardPoints: String = "0",
    val rewardAchievementId: String = "",
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
    val errorMessage: String? = null,
    val teenCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
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
                userRepository.observeUser(uid)
                    .map { it?.clubId }
                    .distinctUntilChanged()
                    .collectLatest { clubId ->
                        if (clubId.isNullOrBlank()) {
                            _state.update { it.copy(teenCount = 0) }
                            return@collectLatest
                        }
                        try {
                            val teens = userRepository.observeClubMembersByRole(clubId, "teen").first()
                            _state.update { it.copy(teenCount = teens.size) }
                        } catch (_: Exception) {}
                    }
            }
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onDueDateChange(v: Date) = _state.update { it.copy(dueDate = v) }
    fun onRewardTypeChange(v: String) = _state.update { it.copy(rewardType = v) }
    fun onRewardTextChange(v: String) = _state.update { it.copy(rewardText = v) }
    fun onRewardPointsChange(v: String) =
        _state.update { it.copy(rewardPoints = v.filter { c -> c.isDigit() }) }
    fun onRewardAchievementIdChange(v: String) =
        _state.update { it.copy(rewardAchievementId = v) }

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
                            rewardType = s.rewardType,
                            rewardText = s.rewardText.trim().ifBlank { null },
                            rewardPoints = s.rewardPoints.toIntOrNull() ?: 0,
                            rewardAchievementId = s.rewardAchievementId.trim().ifBlank { null },
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
