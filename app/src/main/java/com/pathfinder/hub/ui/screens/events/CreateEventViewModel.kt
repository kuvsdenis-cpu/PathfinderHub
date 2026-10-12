package com.pathfinder.hub.ui.screens.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity
import com.pathfinder.hub.data.repository.CustomGroupRepository
import com.pathfinder.hub.data.repository.EventRepository
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
 * A2: опция быстрой группы в dropdown (зеркало CreateTaskViewModel).
 */
data class QuickGroup(
    val code: String,
    val label: String,
    val roles: List<String>,
)

data class CreateEventUiState(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val dateStart: Date? = null,
    val dateEnd: Date? = null,
    val latitude: String = "",
    val longitude: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreated: Boolean = false,
    // A2: групповые приглашения
    val assignedTo: String = "",
    val targetGroup: String? = null,
    val selectedGroupLabel: String? = null,
    val clubMembers: List<com.pathfinder.hub.data.local.entity.core.UserEntity> = emptyList(),
    val showCustomGroupDialog: Boolean = false,
    val customSelectedRoles: Set<String> = emptySet(),
    val customGroups: List<CustomGroupEntity> = emptyList(),
)

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val customGroupRepository: CustomGroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateEventUiState())
    val state: StateFlow<CreateEventUiState> = _state.asStateFlow()

    // A2: быстрые группы (зеркало CreateTaskViewModel)
    val quickGroups: List<QuickGroup> = listOf(
        QuickGroup("all_teens",   "Все следопыты",  listOf("teen")),
        QuickGroup("all_parents", "Все родители",   listOf("parent")),
        QuickGroup("all_staff",   "Все сотрудники", listOf("director", "instructor", "secretary")),
        QuickGroup("all_members", "Все участники",  listOf("teen", "parent", "director", "instructor", "secretary")),
    )

    init {
        loadClubMembers()
        loadCustomGroups()
    }

    private fun loadClubMembers() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId
                if (!clubId.isNullOrBlank()) {
                    userRepository.observeClubMembers(clubId).collect { members ->
                        _state.update { it.copy(clubMembers = members) }
                    }
                }
            } catch (_: Exception) { /* no-op */ }
        }
    }

    private fun loadCustomGroups() {
        val userId = sessionManager.getUserId() ?: return
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

    fun onAssignedToChange(userId: String) = _state.update {
        it.copy(assignedTo = userId, targetGroup = null, selectedGroupLabel = null)
    }

    fun onSelectQuickGroup(group: QuickGroup) = _state.update {
        it.copy(assignedTo = "", targetGroup = group.code, selectedGroupLabel = group.label)
    }

    fun onSelectCustomGroup(group: CustomGroupEntity) = _state.update {
        it.copy(
            assignedTo = "",
            targetGroup = "custom:${group.id}",
            selectedGroupLabel = group.name,
        )
    }

    fun showCustomGroupDialog() = _state.update {
        it.copy(showCustomGroupDialog = true, customSelectedRoles = emptySet())
    }

    fun hideCustomGroupDialog() = _state.update {
        it.copy(showCustomGroupDialog = false, customSelectedRoles = emptySet())
    }

    fun toggleCustomRole(role: String) = _state.update {
        val next = if (role in it.customSelectedRoles) it.customSelectedRoles - role
                   else it.customSelectedRoles + role
        it.copy(customSelectedRoles = next)
    }

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

    private suspend fun membersInGroup(targetGroup: String): List<String> {
        if (targetGroup.startsWith("custom:")) {
            val groupId = targetGroup.removePrefix("custom:")
            return try {
                customGroupRepository.resolveMembers(groupId)
            } catch (_: Exception) { emptyList() }
        }
        val members = _state.value.clubMembers
        return when {
            targetGroup == "all_teens"   -> members.filter { it.role == "teen" }.map { it.id }
            targetGroup == "all_parents" -> members.filter { it.role == "parent" }.map { it.id }
            targetGroup == "all_staff"   -> members.filter { it.role in listOf("director", "instructor", "secretary") }.map { it.id }
            targetGroup == "all_members" -> members.map { it.id }
            targetGroup.startsWith("role:") -> {
                val roles = targetGroup.removePrefix("role:").split(",").map { it.trim() }
                members.filter { it.role in roles }.map { it.id }
            }
            else -> emptyList()
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onLocationChange(v: String) = _state.update { it.copy(location = v) }
    fun onDateStartChange(v: Date) = _state.update { it.copy(dateStart = v) }
    fun onDateEndChange(v: Date) = _state.update { it.copy(dateEnd = v) }
    fun onLatitudeChange(v: String) = _state.update { it.copy(latitude = v) }
    fun onLongitudeChange(v: String) = _state.update { it.copy(longitude = v) }

    fun createEvent() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        if (s.dateStart == null || s.dateEnd == null) {
            _state.update { it.copy(errorMessage = "Укажите дату начала и окончания") }
            return
        }
        if (s.dateEnd.before(s.dateStart)) {
            _state.update { it.copy(errorMessage = "Окончание раньше начала") }
            return
        }

        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId

                val eventId = UUID.randomUUID().toString()
                val event = EventEntity(
                    id = eventId,
                    level = "club",
                    clubId = clubId,
                    type = "meeting",
                    title = s.title.trim(),
                    description = s.description.trim(),
                    dateStart = s.dateStart,
                    dateEnd = s.dateEnd,
                    timezone = java.util.TimeZone.getDefault().id,
                    location = s.location.trim(),
                    isRecurring = false,
                    recurrenceRule = null,
                    parentEventId = null,
                    createdBy = userId,
                    status = "published",
                    autoModerationResult = "approved",
                    moderatedBy = userId,
                    moderatedAt = Date(),
                    reviewDeadline = null,
                    hiddenReasonCode = null,
                    hiddenReasonNote = null,
                    visibility = "club",
                    publishedAt = Date(),
                    qrCode = com.pathfinder.hub.ui.qr.EntityQrHelper.eventPayload(eventId),
                    latitude = s.latitude.toDoubleOrNull(),
                    longitude = s.longitude.toDoubleOrNull(),
                    targetGroup = s.targetGroup,
                )

                val target = s.targetGroup
                if (!target.isNullOrBlank()) {
                    val memberIds = membersInGroup(target)
                    if (memberIds.isEmpty()) {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = "В группе нет участников")
                        }
                        return@launch
                    }
                    eventRepository.createEventForGroup(event, memberIds)
                } else {
                    eventRepository.upsert(event)
                }
                _state.update { it.copy(isLoading = false, isCreated = true) }
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
