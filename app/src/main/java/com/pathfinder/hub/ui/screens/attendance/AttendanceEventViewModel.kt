package com.pathfinder.hub.ui.screens.attendance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttendanceRowUi(
    val userId: String,
    val name: String,
    val status: String,
)

data class AttendanceEventUiState(
    val isLoading: Boolean = true,
    val event: EventEntity? = null,
    val rows: List<AttendanceRowUi> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class AttendanceEventViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""

    private val _state = MutableStateFlow(AttendanceEventUiState())
    val state: StateFlow<AttendanceEventUiState> = _state.asStateFlow()

    init {
        if (eventId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Событие не найдено") }
        } else {
            observeEvent()
            observeParticipants()
        }
    }

    private fun observeEvent() {
        viewModelScope.launch {
            eventRepository.observeEvent(eventId).collect { ev ->
                _state.update { it.copy(event = ev) }
            }
        }
    }

    private fun observeParticipants() {
        viewModelScope.launch {
            eventRepository.observeParticipants(eventId)
                .map { participants ->
                    val users = userRepository.getUsersByIds(participants.map { it.userId })
                        .associateBy { it.id }
                    participants.map { p ->
                        val u = users[p.userId]
                        val name = u?.let { "${it.firstName} ${it.lastName}".trim() }
                            ?.takeIf { it.isNotBlank() }
                            ?: p.userId.take(8)
                        AttendanceRowUi(userId = p.userId, name = name, status = p.status)
                    }
                }
                .collect { rows ->
                    _state.update { it.copy(rows = rows, isLoading = false) }
                }
        }
    }

    fun mark(userId: String, status: String) {
        viewModelScope.launch {
            try {
                eventRepository.updateParticipantStatus(eventId, userId, status)
                _state.update { it.copy(errorMessage = null) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
