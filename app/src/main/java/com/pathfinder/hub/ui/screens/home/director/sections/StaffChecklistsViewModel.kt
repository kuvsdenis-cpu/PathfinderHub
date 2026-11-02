package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.LeaderChecklistEntity
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChecklistWithNames(
    val checklist: LeaderChecklistEntity,
    val childName: String,
    val levelName: String,
    val totalItems: Int,
    val doneItems: Int,
)

data class StaffChecklistsUiState(
    val isLoading: Boolean = true,
    val items: List<ChecklistWithNames> = emptyList(),
    val selected: ChecklistWithNames? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class StaffChecklistsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val levelRepository: LevelRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StaffChecklistsUiState())
    val state: StateFlow<StaffChecklistsUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            observe(uid)
        }
    }

    private fun observe(leaderId: String) {
        viewModelScope.launch {
            levelRepository.observeMyChecklists(leaderId).collect { list ->
                val enriched = list.map { c ->
                    val child = userRepository.getUser(c.childId)
                    val childName = child?.let { "${it.firstName} ${it.lastName}".trim() } ?: c.childId
                    val level = levelRepository.getLevel(c.levelId)
                    val levelName = level?.name ?: c.levelId
                    ChecklistWithNames(
                        checklist = c,
                        childName = childName,
                        levelName = levelName,
                        totalItems = c.items.size,
                        doneItems = c.items.count { it.status == "approved" || it.status == "done" },
                    )
                }
                _state.update { it.copy(isLoading = false, items = enriched) }
            }
        }
    }

    fun open(item: ChecklistWithNames) = _state.update { it.copy(selected = item) }
    fun close() = _state.update { it.copy(selected = null) }
    fun clearMessages() = _state.update { it.copy(errorMessage = null) }
}
