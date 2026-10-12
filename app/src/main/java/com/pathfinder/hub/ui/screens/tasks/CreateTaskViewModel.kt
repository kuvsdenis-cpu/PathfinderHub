package com.pathfinder.hub.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity
import com.pathfinder.hub.data.repository.CustomGroupRepository
import com.pathfinder.hub.data.repository.TaskRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

/**
 * Опция быстрой группы в dropdown.
 */
data class QuickGroup(
    val code: String,          // "all_teens" | "all_parents" | ...
    val label: String,         // "Все следопыты"
    val roles: List<String>,   // ["teen"] — какие роли входят (для подсчёта)
)

data class CreateTaskUiState(
    val title: String = "",
    val description: String = "",
    val assignedTo: String = "",              // userId или "" для группы
    val targetGroup: String? = null,          // null | "all_teens" | "role:teen,instructor"
    val selectedGroupLabel: String? = null,   // для отображения в dropdown
    val dueDate: Date? = null,
    val priority: String = "normal",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreated: Boolean = false,
    val clubMembers: List<UserEntity> = emptyList(),
    val clubMembersLoaded: Boolean = false,
    val currentUserId: String? = null,
    val showCustomGroupDialog: Boolean = false,
    val customSelectedRoles: Set<String> = emptySet(),
    val customGroups: List<CustomGroupEntity> = emptyList(),
)

@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val taskRepository: TaskRepository,
    private val customGroupRepository: CustomGroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateTaskUiState())
    val state: StateFlow<CreateTaskUiState> = _state.asStateFlow()

    // Быстрые группы
    val quickGroups: List<QuickGroup> = listOf(
        QuickGroup("all_teens",   "Все следопыты",  listOf("teen")),
        QuickGroup("all_parents", "Все родители",   listOf("parent")),
        QuickGroup("all_staff",   "Все сотрудники", listOf("director", "instructor", "secretary")),
        QuickGroup("all_members", "Все участники",  listOf("teen", "parent", "director", "instructor", "secretary")),
    )

    init {
        val userId = sessionManager.getUserId()
        _state.update { it.copy(currentUserId = userId) }
        loadClubMembers()
        loadCustomGroups()
    }

    private fun loadClubMembers() {
        val userId = _state.value.currentUserId ?: return
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId
                if (!clubId.isNullOrBlank()) {
                    userRepository.observeClubMembers(clubId).collect { members ->
                        _state.update {
                            it.copy(clubMembers = members, clubMembersLoaded = true)
                        }
                    }
                } else {
                    _state.update { it.copy(clubMembersLoaded = true) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    private fun loadCustomGroups() {
        val userId = _state.value.currentUserId ?: return
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId
                if (!clubId.isNullOrBlank()) {
                    customGroupRepository.observeActiveByClub(clubId).collect { groups ->
                        _state.update { it.copy(customGroups = groups) }
                    }
                }
            } catch (_: Exception) { /* no-op */ }
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onDueDateChange(v: Date) = _state.update { it.copy(dueDate = v) }
    fun onPriorityChange(v: String) = _state.update { it.copy(priority = v) }

    /** Выбор конкретного пользователя. */
    fun onAssignedToChange(userId: String) = _state.update {
        it.copy(
            assignedTo = userId,
            targetGroup = null,
            selectedGroupLabel = null,
        )
    }

    /** Выбор быстрой группы. */
    fun onSelectQuickGroup(group: QuickGroup) = _state.update {
        it.copy(
            assignedTo = "",
            targetGroup = group.code,
            selectedGroupLabel = group.label,
        )
    }

    /** Выбор пользовательской группы (B3). */
    fun onSelectCustomGroup(group: CustomGroupEntity) = _state.update {
        it.copy(
            assignedTo = "",
            targetGroup = "custom:${group.id}",
            selectedGroupLabel = group.name,
        )
    }

    /** Открыть диалог кастомной группы. */
    fun showCustomGroupDialog() = _state.update {
        it.copy(showCustomGroupDialog = true, customSelectedRoles = emptySet())
    }

    fun hideCustomGroupDialog() = _state.update {
        it.copy(showCustomGroupDialog = false, customSelectedRoles = emptySet())
    }

    fun toggleCustomRole(role: String) = _state.update {
        val next = if (role in it.customSelectedRoles)
            it.customSelectedRoles - role
        else
            it.customSelectedRoles + role
        it.copy(customSelectedRoles = next)
    }

    /** Подтвердить кастомную группу. */
    fun confirmCustomGroup() {
        val roles = _state.value.customSelectedRoles
        if (roles.isEmpty()) {
            _state.update { it.copy(errorMessage = "Выберите хотя бы одну роль") }
            return
        }
        val code = "role:" + roles.sorted().joinToString(",")
        val label = roles.sorted().joinToString(", ") { roleName(it) }
        _state.update {
            it.copy(
                assignedTo = "",
                targetGroup = code,
                selectedGroupLabel = label,
                showCustomGroupDialog = false,
                customSelectedRoles = emptySet(),
            )
        }
    }

    /** Определить список userId для группы. */
    private suspend fun membersInGroup(targetGroup: String): List<String> {
        if (targetGroup.startsWith("custom:")) {
            val groupId = targetGroup.removePrefix("custom:")
            return try {
                customGroupRepository.resolveMembers(groupId)
            } catch (_: Exception) { emptyList() }
        }
        val members = _state.value.clubMembers
        return when {
            targetGroup == "all_teens" ->
                members.filter { it.role == "teen" }.map { it.id }
            targetGroup == "all_parents" ->
                members.filter { it.role == "parent" }.map { it.id }
            targetGroup == "all_staff" ->
                members.filter { it.role in listOf("director", "instructor", "secretary") }.map { it.id }
            targetGroup == "all_members" ->
                members.map { it.id }
            targetGroup.startsWith("role:") -> {
                val roles = targetGroup.removePrefix("role:").split(",").map { it.trim() }
                members.filter { it.role in roles }.map { it.id }
            }
            else -> emptyList()
        }
    }

    fun createTask() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название задачи") }
            return
        }
        val isGroup = !s.targetGroup.isNullOrBlank()
        if (!isGroup && s.assignedTo.isBlank()) {
            _state.update { it.copy(errorMessage = "Выберите исполнителя или группу") }
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

                if (isGroup) {
                    val memberIds = membersInGroup(s.targetGroup!!)
                    if (memberIds.isEmpty()) {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = "В группе нет участников")
                        }
                        return@launch
                    }
                    val task = TaskEntity(
                        id = UUID.randomUUID().toString(),
                        level = "club",
                        clubId = clubId,
                        eventId = null,
                        title = s.title.trim(),
                        description = s.description.trim(),
                        assignedTo = "",
                        assignedBy = directorId,
                        dueDate = s.dueDate,
                        priority = s.priority,
                        status = "assigned",
                        createdAt = Date(),
                        confirmedBy = null,
                        confirmedAt = null,
                        pendingSync = true,
                        targetGroup = s.targetGroup,
                    )
                    val result = taskRepository.createTaskForGroup(task, memberIds)
                    result.fold(
                        onSuccess = {
                            _state.update { it.copy(isLoading = false, isCreated = true) }
                        },
                        onFailure = { e ->
                            _state.update {
                                it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка")
                            }
                        },
                    )
                } else {
                    val task = TaskEntity(
                        id = UUID.randomUUID().toString(),
                        level = "club",
                        clubId = clubId,
                        eventId = null,
                        title = s.title.trim(),
                        description = s.description.trim(),
                        assignedTo = s.assignedTo,
                        assignedBy = directorId,
                        dueDate = s.dueDate,
                        priority = s.priority,
                        status = "assigned",
                        createdAt = Date(),
                        confirmedBy = null,
                        confirmedAt = null,
                        pendingSync = true,
                        targetGroup = null,
                    )
                    taskRepository.upsert(task)
                    _state.update { it.copy(isLoading = false, isCreated = true) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка создания")
                }
            }
        }
    }

    companion object {
        fun roleName(role: String): String = when (role) {
            "teen" -> "Следопыт"
            "parent" -> "Родитель"
            "instructor" -> "Наставник"
            "director" -> "Директор"
            "secretary" -> "Секретарь"
            else -> role
        }
    }
}
