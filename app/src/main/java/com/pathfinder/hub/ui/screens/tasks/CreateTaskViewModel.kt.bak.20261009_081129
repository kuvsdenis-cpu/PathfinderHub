package com.pathfinder.hub.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.repository.TaskRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class CreateTaskUiState(
    val title: String = "",
    val description: String = "",
    val assignedTo: String = "",
    val dueDate: String = "",
    val priority: String = "normal",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreated: Boolean = false,
    val clubMembers: List<UserEntity> = emptyList(),
    val currentUserId: String? = null
)

@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateTaskUiState())
    val state: StateFlow<CreateTaskUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        _state.update { it.copy(currentUserId = userId) }
        loadClubMembers()
    }

    private fun loadClubMembers() {
        val userId = _state.value.currentUserId ?: return
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId
                if (!clubId.isNullOrBlank()) {
                    userRepository.observeClubMembers(clubId).collect { members ->
                        _state.update { it.copy(clubMembers = members) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun onAssignedToChange(value: String) = _state.update { it.copy(assignedTo = value) }
    fun onDueDateChange(value: String) = _state.update { it.copy(dueDate = value) }
    fun onPriorityChange(value: String) = _state.update { it.copy(priority = value) }

    fun createTask() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название задачи") }
            return
        }
        if (s.assignedTo.isBlank()) {
            _state.update { it.copy(errorMessage = "Выберите исполнителя") }
            return
        }
        val directorId = s.currentUserId
        if (directorId == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(directorId)
                val clubId = currentUser?.clubId
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dueDateParsed = try {
                    if (s.dueDate.isNotBlank()) fmt.parse(s.dueDate) else null
                } catch (e: Exception) { null }

                val task = TaskEntity(
                    id = UUID.randomUUID().toString(),
                    level = "club",
                    clubId = clubId,
                    eventId = null,
                    title = s.title.trim(),
                    description = s.description.trim(),
                    assignedTo = s.assignedTo,
                    assignedBy = directorId,
                    dueDate = dueDateParsed,
                    priority = s.priority,
                    status = "assigned",
                    createdAt = Date(),
                    confirmedBy = null,
                    confirmedAt = null,
                    pendingSync = true
                )

                taskRepository.upsert(task)
                _state.update { it.copy(isLoading = false, isCreated = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка создания") }
            }
        }
    }
}