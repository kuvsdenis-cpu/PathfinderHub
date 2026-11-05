package com.pathfinder.hub.ui.screens.mentees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MenteeUi(
    val user: UserEntity,
    val completedLevels: Int,
    val earnedAchievements: Int,
)

data class MenteesUiState(
    val isLoading: Boolean = true,
    val mentees: List<MenteeUi> = emptyList(),
    val availableMembers: List<UserEntity> = emptyList(),
    val showAddDialog: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class MenteesViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MenteesUiState())
    val state: StateFlow<MenteesUiState> = _state.asStateFlow()

    private val mentorId: String? = sessionManager.getUserId()

    init {
        val mid = mentorId
        if (mid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
        } else {
            observeMentees(mid)
        }
    }

    private fun observeMentees(mid: String) {
        viewModelScope.launch {
            userRepository.observeMentees(mid).collect { list ->
                val uiList = list.map { u ->
                    val levels = try {
                        levelRepository.observeCompletions(u.id).first().size
                    } catch (_: Exception) { 0 }
                    val achievements = try {
                        achievementRepository.observeEarned(u.id).first().size
                    } catch (_: Exception) { 0 }
                    MenteeUi(u, levels, achievements)
                }
                _state.update { it.copy(mentees = uiList, isLoading = false) }
            }
        }
    }

    fun openAddDialog() {
        val mid = mentorId ?: return
        viewModelScope.launch {
            val me = userRepository.getUser(mid) ?: return@launch
            val clubId = me.clubId ?: me.clubIds.firstOrNull() ?: return@launch
            val members = userRepository.observeClubMembers(clubId).first()
                .filter { it.id != mid && it.mentorId == null && it.role == "teen" }
            _state.update { it.copy(showAddDialog = true, availableMembers = members) }
        }
    }

    fun closeAddDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun addMentee(userId: String) {
        val mid = mentorId ?: return
        viewModelScope.launch {
            try {
                userRepository.setMentor(userId, mid)
                _state.update { it.copy(showAddDialog = false) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun removeMentee(userId: String) {
        viewModelScope.launch {
            try {
                userRepository.setMentor(userId, null)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }
}
