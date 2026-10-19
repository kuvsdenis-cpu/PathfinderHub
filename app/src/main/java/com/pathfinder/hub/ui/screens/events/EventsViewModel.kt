package com.pathfinder.hub.ui.screens.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsUiState(
    val isLoading: Boolean = true,
    val activeTab: Int = 0,
    val events: List<EventEntity> = emptyList(),
    val archived: List<EventEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(EventsUiState())
    val state: StateFlow<EventsUiState> = _state.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            userRepository.observeUser(userId).collect { user ->
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, events = emptyList(), archived = emptyList()) }
                    return@collect
                }
                val now = System.currentTimeMillis()
                val end = now + 365L * 24 * 3600 * 1000

                launch {
                    eventRepository.observeEventsForUser(clubId, userId, 0L, end).collect { list ->
                        _state.update { it.copy(isLoading = false, events = list) }
                    }
                }
                launch {
                    eventRepository.observeArchivedByClub(clubId).collect { list ->
                        _state.update { it.copy(archived = list) }
                    }
                }
            }
        }
    }

    fun setTab(idx: Int) = _state.update { it.copy(activeTab = idx) }

    fun archiveEvent(id: String) {
        viewModelScope.launch { eventRepository.archiveEvent(id) }
    }

    fun restoreEvent(id: String) {
        viewModelScope.launch { eventRepository.restoreEvent(id) }
    }
}
