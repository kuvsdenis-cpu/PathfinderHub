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

/** Задача + её per-user статус из assignment. */
data class TaskWithAssignment(
    val task: TaskEntity,
    val assignmentStatus: String?,   // "assigned" | "accepted" | "done" | null
)

data class MyTasksUiState(
    val isLoading: Boolean = true,
    val activeTab: Int = 0,
    val tasks: List<TaskWithAssignment> = emptyList(),
    val archived: List<TaskWithAssignment> = emptyList(),
)

@HiltViewModel
class MyTasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(MyTasksUiState())
    val state: StateFlow<MyTasksUiState> = _state.asStateFlow()

    private var userId: String? = null

    init {
        userId = sessionManager.getUserId()
        val uid = userId
        if (uid == null) {
            _state.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                launch {
                    taskRepository.observeMyTasks(uid).collect { list ->
                        _state.update { it.copy(isLoading = false, tasks = emptyList()) }
                        // Подгружаем статусы из assignments
                        val enriched = list.map { t ->
                            val a = taskRepository.getAssignment(t.id, uid)
                            TaskWithAssignment(t, a?.status)
                        }
                        _state.update { it.copy(isLoading = false, tasks = enriched) }
                    }
                }
                launch {
                    taskRepository.observeMyArchivedTasks(uid).collect { list ->
                        val enriched = list.map { t -> TaskWithAssignment(t, null) }
                        _state.update { it.copy(archived = enriched) }
                    }
                }
            }
        }
    }

    fun setTab(idx: Int) = _state.update { it.copy(activeTab = idx) }

    fun acceptTask(taskId: String) {
        val uid = userId ?: return
        viewModelScope.launch {
            taskRepository.acceptAssignment(taskId, uid)
            refreshTask(taskId)
        }
    }

    fun completeTask(taskId: String) {
        val uid = userId ?: return
        viewModelScope.launch {
            taskRepository.completeAssignment(taskId, uid)
            refreshTask(taskId)
        }
    }

    fun archiveTask(id: String) {
        viewModelScope.launch { taskRepository.archiveTask(id) }
    }

    fun restoreTask(id: String) {
        viewModelScope.launch { taskRepository.restoreTask(id) }
    }

    private suspend fun refreshTask(taskId: String) {
        val uid = userId ?: return
        val t = taskRepository.getTask(taskId) ?: return
        val a = taskRepository.getAssignment(taskId, uid)
        val newItem = TaskWithAssignment(t, a?.status)
        _state.update { s ->
            s.copy(tasks = s.tasks.map { if (it.task.id == taskId) newItem else it })
        }
    }
}
