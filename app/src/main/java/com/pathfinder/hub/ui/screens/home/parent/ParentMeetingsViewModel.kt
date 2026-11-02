package com.pathfinder.hub.ui.screens.home.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.parent.ParentAttendee
import com.pathfinder.hub.data.local.entity.parent.ParentMeetingEntity
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.planning.EventParticipantEntity
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

data class ParentMeetingItem(
    val meeting: ParentMeetingEntity,
    val event: EventEntity?,
    val attendee: ParentAttendee?,
    val participant: EventParticipantEntity? = null,
)

data class ParentMeetingsUiState(
    val isLoading: Boolean = true,
    val items: List<ParentMeetingItem> = emptyList(),
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ParentMeetingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val parentRepository: ParentRepository,
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentMeetingsUiState())
    val state: StateFlow<ParentMeetingsUiState> = _state.asStateFlow()

    private val parentId: String? = sessionManager.getUserId()

    init {
        val uid = parentId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(parentId: String) {
        viewModelScope.launch {
            val user = userRepository.getUser(parentId)
            val clubId = user?.clubId ?: ""
            val now = System.currentTimeMillis()
            val end = now + 365L * 24 * 3600 * 1000

            eventRepository.observeEventsForUser(clubId, parentId, 0L, end).collect { events ->
                val items = events.mapNotNull { e ->
                    val meeting = try {
                        parentRepository.getMeetingByEventId(e.id)
                    } catch (_: Exception) { null } ?: return@mapNotNull null

                    val participant = try {
                        eventRepository.getParticipant(e.id, parentId)
                    } catch (_: Exception) { null }

                    // (v-1): показываем, если participant есть ИЛИ событие без targetGroup
                    val visible = participant != null || e.targetGroup == null
                    if (!visible) return@mapNotNull null

                    ParentMeetingItem(
                        meeting = meeting,
                        event = e,
                        attendee = meeting.attendees.find { it.parentId == parentId },
                        participant = participant,
                    )
                }
                _state.update { it.copy(isLoading = false, items = items) }
            }
        }
    }

    /**
     * Родитель подтверждает/отклоняет участие.
     * Пишем в EventParticipantEntity (источник правды), синхронизируем ParentAttendee (s-2).
     */
    fun confirm(eventId: String, confirmed: Boolean) {
        val uid = parentId ?: return
        viewModelScope.launch {
            try {
                eventRepository.updateParticipantStatus(
                    eventId = eventId,
                    userId = uid,
                    status = if (confirmed) "accepted" else "declined",
                )
                syncAttendee(eventId, uid) { att ->
                    att.copy(confirmed = confirmed)
                }
                _state.update {
                    it.copy(infoMessage = if (confirmed) "Участие подтверждено" else "Отмечено: не смогу")
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /**
     * Добавить вопрос. Пишем в EventParticipantEntity (источник правды),
     * синхронизируем ParentAttendee.questions (s-2).
     */
    fun addQuestion(eventId: String, question: String) {
        if (question.isBlank()) return
        val uid = parentId ?: return
        viewModelScope.launch {
            try {
                val participant = eventRepository.getParticipant(eventId, uid)
                val current = participant?.questions ?: emptyList()
                val next = current + question.trim()
                eventRepository.updateParticipantQuestions(eventId, uid, next)
                syncAttendee(eventId, uid) { att ->
                    att.copy(questions = att.questions + question.trim())
                }
                _state.update { it.copy(infoMessage = "Вопрос добавлен") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /**
     * (s-2): синхронизировать ParentAttendee. Если его нет — добавить.
     */
    private suspend fun syncAttendee(
        eventId: String,
        parentId: String,
        transform: (ParentAttendee) -> ParentAttendee,
    ) {
        val meeting = parentRepository.getMeetingByEventId(eventId) ?: return
        val existing = meeting.attendees.find { it.parentId == parentId }
        val updated = if (existing == null) {
            meeting.attendees + transform(
                ParentAttendee(parentId = parentId, confirmed = false, questions = emptyList())
            )
        } else {
            meeting.attendees.map { att ->
                if (att.parentId == parentId) transform(att) else att
            }
        }
        parentRepository.upsertMeeting(meeting.copy(attendees = updated))
    }

    fun clearMessages() = _state.update { it.copy(infoMessage = null, errorMessage = null) }
}
