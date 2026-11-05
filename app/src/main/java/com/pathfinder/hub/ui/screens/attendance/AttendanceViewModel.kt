package com.pathfinder.hub.ui.screens.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttendanceUiState(
    val isLoading: Boolean = true,
    val events: List<EventEntity> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AttendanceUiState())
    val state: StateFlow<AttendanceUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
        } else {
            loadEvents(userId)
        }
    }

    private fun loadEvents(userId: String) {
        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            val clubId = user?.clubId ?: user?.clubIds?.firstOrNull()
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            val now = System.currentTimeMillis()
            val from = now - 30L * 24 * 3600 * 1000
            val to = now + 7L * 24 * 3600 * 1000
            eventRepository.observeEventsInRange(clubId, from, to).collect { events ->
                _state.update { it.copy(events = events, isLoading = false) }
            }
        }
    }
}
