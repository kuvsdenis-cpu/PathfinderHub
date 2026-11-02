package com.pathfinder.hub.ui.screens.home.conference.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.conference.ConferenceEventEntity
import com.pathfinder.hub.data.repository.ConferenceEventRepository
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

data class CreateConferenceEventUiState(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val level: String = "conference",
    val dateStart: Date? = null,
    val dateEnd: Date? = null,
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CreateConferenceEventViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val repo: ConferenceEventRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateConferenceEventUiState())
    val state: StateFlow<CreateConferenceEventUiState> = _state.asStateFlow()

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onLocation(v: String) = _state.update { it.copy(location = v) }
    fun onLevel(v: String) = _state.update { it.copy(level = v) }
    fun onDateStart(v: Date) = _state.update { it.copy(dateStart = v) }
    fun onDateEnd(v: Date) = _state.update { it.copy(dateEnd = v) }

    fun save() {
        val s = _state.value
        val uid = sessionManager.getUserId() ?: return

        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        if (s.dateStart == null || s.dateEnd == null) {
            _state.update { it.copy(errorMessage = "Укажите даты начала и окончания") }
            return
        }
        if (s.dateEnd.before(s.dateStart)) {
            _state.update { it.copy(errorMessage = "Окончание раньше начала") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val me = userRepository.getUser(uid)
                val confId = me?.conferenceId
                if (confId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Конференция не найдена") }
                    return@launch
                }
                val now = Date()
                val entity = ConferenceEventEntity(
                    id = UUID.randomUUID().toString(),
                    conferenceId = confId,
                    level = s.level,
                    type = "meeting",
                    title = s.title.trim(),
                    description = s.description.trim(),
                    dateStart = s.dateStart,
                    dateEnd = s.dateEnd,
                    timezone = java.util.TimeZone.getDefault().id,
                    location = s.location.trim(),
                    invitedClubs = emptyList(),
                    visibility = "all",
                    createdBy = uid,
                    status = "published",
                    publishedAt = now,
                    pendingSync = true,
                    createdAt = now,
                    updatedAt = now,
                )
                repo.upsert(entity)
                _state.update { it.copy(isLoading = false, isCreated = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }
}
