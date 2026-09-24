package com.pathfinder.hub.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyTasksUiState(
    val isLoading: Boolean = true,
    val tasks: List<TaskEntity> = emptyList()
)

@HiltViewModel
class MyTasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(MyTasksUiState())
    val state: StateFlow<MyTasksUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                taskRepository.observeAssignedTasks(userId).collect { list ->
                    _state.update { it.copy(isLoading = false, tasks = list) }
                }
            }
        }
    }

    fun acceptTask(id: String) {
        viewModelScope.launch {
            taskRepository.updateStatus(id, "accepted")
        }
    }

    fun completeTask(id: String) {
        viewModelScope.launch {
            taskRepository.updateStatus(id, "done")
        }
    }
}