package com.pathfinder.hub.ui.screens.home.conference.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.conference.ConferenceEventEntity
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.ConferenceEventRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class ClubStat(
    val club: ClubEntity,
    val totalMembers: Int,
    val teens: Int,
    val instructors: Int,
    val directors: Int,
)

data class ConferenceAnalyticsUiState(
    val isLoading: Boolean = true,
    val conferenceId: String? = null,
    val totalClubs: Int = 0,
    val totalMembers: Int = 0,
    val teens: Int = 0,
    val parents: Int = 0,
    val directors: Int = 0,
    val instructors: Int = 0,
    val secretaries: Int = 0,
    val eventsTotal: Int = 0,
    val eventsUpcoming: Int = 0,
    val eventsPast: Int = 0,
    val eventsCancelled: Int = 0,
    val topClubs: List<ClubStat> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ConferenceAnalyticsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val eventRepository: ConferenceEventRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConferenceAnalyticsUiState())
    val state: StateFlow<ConferenceAnalyticsUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(uid: String) {
        viewModelScope.launch {
            val me = userRepository.getUser(uid)
            val confId = me?.conferenceId
            if (confId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Конференция не найдена") }
                return@launch
            }
            _state.update { it.copy(conferenceId = confId) }

            // ----- Клубы -----
            clubRepository.observeClubsByConference(confId).collect { clubs ->
                _state.update { it.copy(totalClubs = clubs.size) }

                // Для каждого клуба — метрики по участникам
                val stats = clubs.map { club ->
                    val members = try {
                        userRepository.observeClubMembers(club.id).first()
                    } catch (_: Exception) { emptyList() }
                    ClubStat(
                        club = club,
                        totalMembers = members.size,
                        teens = members.count { it.role == "teen" },
                        instructors = members.count { it.role == "instructor" },
                        directors = members.count { it.role == "director" },
                    )
                }
                _state.update {
                    it.copy(topClubs = stats.sortedByDescending { s -> s.totalMembers }.take(5))
                }
            }

            // ----- Участники конференции -----
            userRepository.observeConferenceMembers(confId).collect { members ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        totalMembers = members.size,
                        teens = members.count { u -> u.role == "teen" },
                        parents = members.count { u -> u.role == "parent" },
                        directors = members.count { u -> u.role == "director" },
                        instructors = members.count { u -> u.role == "instructor" },
                        secretaries = members.count {
                            u -> u.role == "secretary" || u.role == "conference_secretary"
                        },
                    )
                }
            }

            // ----- Мероприятия -----
            eventRepository.observeAll(confId).collect { events ->
                val now = System.currentTimeMillis()
                val upcoming = events.count {
                    it.status == "published" && it.dateStart.time > now
                }
                val past = events.count {
                    it.status == "published" && it.dateEnd.time <= now
                }
                val cancelled = events.count { it.status == "cancelled" }
                _state.update {
                    it.copy(
                        eventsTotal = events.size,
                        eventsUpcoming = upcoming,
                        eventsPast = past,
                        eventsCancelled = cancelled,
                    )
                }
            }
        }
    }
}
