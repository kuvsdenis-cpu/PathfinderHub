package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.planning.EventParticipantEntity
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsTopEvent(
    val eventId: String,
    val title: String,
    val attended: Int,
    val total: Int,
)

data class AnalyticsTopMember(
    val userId: String,
    val name: String,
    val attended: Int,
)

data class DirectorAnalyticsUiState(
    val isLoading: Boolean = true,
    val membersCount: Int = 0,
    val eventsCount: Int = 0,
    val totalAttendance: Int = 0,
    val averageAttendance: Double = 0.0,
    val topEvents: List<AnalyticsTopEvent> = emptyList(),
    val topMembers: List<AnalyticsTopMember> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class DirectorAnalyticsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorAnalyticsUiState())
    val state: StateFlow<DirectorAnalyticsUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
        } else {
            viewModelScope.launch { load(uid) }
        }
    }

    private suspend fun load(uid: String) {
        try {
            val user = userRepository.getUser(uid)
            val clubId = user?.clubId ?: user?.clubIds?.firstOrNull()
            if (clubId == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return
            }
            val now = System.currentTimeMillis()
            val from = now - 30L * 24 * 3600 * 1000
            val to = now + 7L * 24 * 3600 * 1000
            val events: List<EventEntity> = eventRepository.observeEventsInRange(clubId, from, to).first()
            val members = userRepository.observeClubMembers(clubId).first()

            var totalAttended = 0
            var totalParticipants = 0
            val topEventsList = mutableListOf<AnalyticsTopEvent>()
            val memberAttendance = mutableMapOf<String, Int>()

            for (ev in events) {
                val parts: List<EventParticipantEntity> =
                    eventRepository.observeParticipants(ev.id).first()
                val attended = parts.count { it.status == "attended" }
                totalAttended += attended
                totalParticipants += parts.size
                parts.forEach { p ->
                    if (p.status == "attended") {
                        memberAttendance[p.userId] = (memberAttendance[p.userId] ?: 0) + 1
                    }
                }
                topEventsList.add(AnalyticsTopEvent(ev.id, ev.title, attended, parts.size))
            }

            val topEvents = topEventsList.sortedByDescending { it.attended }.take(5)
            val topMembers = memberAttendance.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { (userId, attended) ->
                    val u = members.firstOrNull { it.id == userId }
                    val name = u?.let { "${it.firstName} ${it.lastName}".trim().ifBlank { it.email } }
                        ?: userId.take(8)
                    AnalyticsTopMember(userId, name, attended)
                }

            val avg = if (totalParticipants == 0) 0.0
            else totalAttended.toDouble() / totalParticipants * 100.0

            _state.update {
                it.copy(
                    isLoading = false,
                    membersCount = members.size,
                    eventsCount = events.size,
                    totalAttendance = totalAttended,
                    averageAttendance = avg,
                    topEvents = topEvents,
                    topMembers = topMembers,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, errorMessage = e.message) }
        }
    }
}
