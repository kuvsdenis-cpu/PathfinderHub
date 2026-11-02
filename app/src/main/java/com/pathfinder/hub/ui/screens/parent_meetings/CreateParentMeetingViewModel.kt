package com.pathfinder.hub.ui.screens.parent_meetings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.parent.ParentAttendee
import com.pathfinder.hub.data.local.entity.parent.ParentMeetingEntity
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity
import com.pathfinder.hub.data.repository.CustomGroupRepository
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateParentMeetingUiState(
    val isLoading: Boolean = true,
    val events: List<EventEntity> = emptyList(),
    val parents: List<UserEntity> = emptyList(),
    val selectedEventId: String? = null,
    val selectedParentIds: Set<String> = emptySet(),
    val agenda: String = "",
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    // B4.2
    val customGroups: List<CustomGroupEntity> = emptyList(),
    val groupMode: String = "all",  // "all" | "custom" | "manual"
    val selectedGroupId: String? = null,
    val selectedGroupLabel: String? = null,
)

@HiltViewModel
class CreateParentMeetingViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val parentRepository: ParentRepository,
    private val customGroupRepository: CustomGroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateParentMeetingUiState())
    val state: StateFlow<CreateParentMeetingUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }
        viewModelScope.launch {
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            // События клуба
            eventRepository.observeEventsInRange(
                clubId,
                System.currentTimeMillis() - 7L * 24 * 3600 * 1000,
                System.currentTimeMillis() + 365L * 24 * 3600 * 1000,
            ).collect { events ->
                _state.update { it.copy(events = events) }
            }
        }
        viewModelScope.launch {
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId ?: return@launch
            userRepository.observeClubMembersByRole(clubId, "parent").collect { list ->
                _state.update { it.copy(parents = list, isLoading = false) }
            }
        }
        viewModelScope.launch {
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId ?: return@launch
            customGroupRepository.observeActiveByClub(clubId).collect { groups ->
                _state.update { it.copy(customGroups = groups) }
            }
        }
    }

    fun onEventSelected(id: String) = _state.update { it.copy(selectedEventId = id) }

    fun toggleParent(id: String) {
        _state.update {
            val cur = it.selectedParentIds
            val next = if (id in cur) cur - id else cur + id
            it.copy(selectedParentIds = next)
        }
    }

    fun selectAllParents() {
        _state.update { it.copy(selectedParentIds = it.parents.map { p -> p.id }.toSet()) }
    }

    fun clearAllParents() = _state.update { it.copy(selectedParentIds = emptySet()) }

    fun onSelectAllParents() = _state.update {
        it.copy(groupMode = "all", selectedGroupId = null, selectedGroupLabel = null)
    }

    fun onSelectCustomGroup(group: CustomGroupEntity) = _state.update {
        it.copy(
            groupMode = "custom",
            selectedGroupId = group.id,
            selectedGroupLabel = group.name,
        )
    }

    fun onSelectManual() = _state.update {
        it.copy(groupMode = "manual", selectedGroupId = null, selectedGroupLabel = null)
    }

    fun onAgenda(v: String) = _state.update { it.copy(agenda = v) }

    fun save() {
        val s = _state.value
        val clubId = s.events.find { it.id == s.selectedEventId }?.clubId
        if (s.selectedEventId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Выберите событие") }
            return
        }
        if (clubId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Клуб события не найден") }
            return
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                // B4.2: определяем список parentId по режиму
                val parentIds: List<String> = when (s.groupMode) {
                    "all" -> s.parents.map { it.id }
                    "custom" -> {
                        val gid = s.selectedGroupId
                        if (gid.isNullOrBlank()) emptyList()
                        else {
                            val ids = customGroupRepository.resolveMembers(gid)
                            val parentsSet = s.parents.map { it.id }.toSet()
                            ids.filter { it in parentsSet }
                        }
                    }
                    "manual" -> s.selectedParentIds.toList()
                    else -> emptyList()
                }

                if (parentIds.isEmpty()) {
                    _state.update {
                        it.copy(isSaving = false, errorMessage = "В группе нет родителей")
                    }
                    return@launch
                }

                val attendees = parentIds.map { pid ->
                    ParentAttendee(parentId = pid, confirmed = false, questions = emptyList())
                }
                val entity = ParentMeetingEntity(
                    eventId = s.selectedEventId!!,
                    clubId = clubId!!,
                    agenda = s.agenda.trim(),
                    attendees = attendees,
                )
                parentRepository.upsertMeeting(entity)
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }
}
