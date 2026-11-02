package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.repository.TaskRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskWithStats(
    val task: TaskEntity,
    val assignedCount: Int,
    val acceptedCount: Int,
    val doneCount: Int,
    val confirmedCount: Int,
) {
    val totalCount: Int get() = assignedCount + acceptedCount + doneCount + confirmedCount
    val doneOrConfirmed: Int get() = doneCount + confirmedCount
}

data class TaskAssignee(
    val userId: String,
    val name: String,
    val status: String,
)

data class StaffTasksUiState(
    val isLoading: Boolean = true,
    val tasks: List<TaskWithStats> = emptyList(),
    val selectedTask: TaskWithStats? = null,
    val assignees: List<TaskAssignee> = emptyList(),
    val loadingAssignees: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class StaffTasksViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StaffTasksUiState())
    val state: StateFlow<StaffTasksUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            observeTasks(uid)
        }
    }

    private fun observeTasks(userId: String) {
        viewModelScope.launch {
            userRepository.observeUser(userId)
                .map { it?.clubId }
                .distinctUntilChanged()
                .collectLatest { clubId ->
                    if (clubId.isNullOrBlank()) {
                        _state.update { it.copy(isLoading = false, tasks = emptyList()) }
                        return@collectLatest
                    }
                    taskRepository.observeActiveClubTasks(clubId).collect { list ->
                        val withStats = list.map { task ->
                            val assignments = taskRepository.getAssignments(task.id)
                            TaskWithStats(
                                task = task,
                                assignedCount = assignments.count { it.status == "assigned" },
                                acceptedCount = assignments.count { it.status == "accepted" },
                                doneCount = assignments.count { it.status == "done" },
                                confirmedCount = assignments.count { it.status == "confirmed" },
                            )
                        }
                        _state.update { it.copy(isLoading = false, tasks = withStats) }
                    }
                }
        }
    }

    fun openTask(item: TaskWithStats) {
        _state.update { it.copy(selectedTask = item, loadingAssignees = true, assignees = emptyList()) }
        viewModelScope.launch {
            try {
                val assignments = taskRepository.getAssignments(item.task.id)
                val list = assignments.map { a ->
                    val u = userRepository.getUser(a.userId)
                    TaskAssignee(
                        userId = a.userId,
                        name = u?.let { "${it.firstName} ${it.lastName}".trim() } ?: a.userId,
                        status = a.status,
                    )
                }
                _state.update { it.copy(loadingAssignees = false, assignees = list) }
            } catch (e: Exception) {
                _state.update { it.copy(loadingAssignees = false, errorMessage = e.message) }
            }
        }
    }

    fun closeTask() = _state.update { it.copy(selectedTask = null, assignees = emptyList()) }

    fun clearMessages() = _state.update { it.copy(errorMessage = null) }
}
