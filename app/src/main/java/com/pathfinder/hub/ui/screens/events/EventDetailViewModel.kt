package com.pathfinder.hub.ui.screens.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.parent.EventConsentEntity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/**
 * Строка участника с резолвнутым именем и человекочитаемым статусом.
 */
data class AttendeeRow(
    val userId: String,
    val displayName: String,
    val status: String,
    val statusLabel: String,
    val statusEmoji: String,
)

/**
 * Вопрос от родителя по встрече.
 * Источник: EventParticipantEntity.questions (истина) +
 *           ParentMeetingEntity.attendees.questions (кэш).
 * Дедуп по (parentId, text).
 */
data class ParentQuestion(
    val parentId: String,
    val parentName: String,
    val text: String,
)

data class EventDetailUiState(
    val isLoading: Boolean = true,
    val event: EventEntity? = null,
    val currentUser: UserEntity? = null,
    val isRequesting: Boolean = false,
    val consentMessage: String? = null,
    val errorMessage: String? = null,
    // A2: per-user участие
    val myParticipant: EventParticipantEntity? = null,
    val participants: List<EventParticipantEntity> = emptyList(),
    val canManageAttendance: Boolean = false,

    // A3: встреча с родителями (для staff)
    val parentMeeting: ParentMeetingEntity? = null,
    val participantRows: List<AttendeeRow> = emptyList(),
    val attendeeRows: List<AttendeeRow> = emptyList(),
    val parentQuestions: List<ParentQuestion> = emptyList(),
)

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val parentRepository: ParentRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""
    private val _state = MutableStateFlow(EventDetailUiState())
    val state: StateFlow<EventDetailUiState> = _state.asStateFlow()

    private val currentUserId: String? = sessionManager.getUserId()

    init {
        viewModelScope.launch {
            val e = eventRepository.getEvent(eventId)
            val u = currentUserId?.let { userRepository.getUser(it) }
            val canManage = u != null && (
                u.role in listOf("director", "co-director", "instructor") || u.isOwnerClub
            )
            _state.update {
                it.copy(isLoading = false, event = e, currentUser = u, canManageAttendance = canManage)
            }
        }
        viewModelScope.launch {
            eventRepository.observeParticipants(eventId).collect { list ->
                _state.update { st ->
                    st.copy(
                        participants = list,
                        myParticipant = list.find { p -> p.userId == currentUserId },
                    )
                }
                refreshDerived()
            }
        }
        // A3: встреча с родителями — один раз, не Flow.
        viewModelScope.launch {
            val meeting = try {
                parentRepository.getMeetingByEventId(eventId)
            } catch (_: Exception) { null }
            _state.update { it.copy(parentMeeting = meeting) }
            refreshDerived()
        }
    }

    /**
     * Пересобрать participantRows, attendeeRows, parentQuestions.
     * Вызывается, когда приходят participants или parentMeeting.
     */
    private suspend fun refreshDerived() {
        val s = _state.value
        val meeting = s.parentMeeting

        // ---- Имена участников события ----
        val participantIds = s.participants.map { it.userId }.distinct()
        val participantUsers = if (participantIds.isEmpty()) emptyList()
            else userRepository.getUsersByIds(participantIds)
        val nameByUserId = participantUsers.associate { u ->
            u.id to "${u.firstName} ${u.lastName}".trim().ifBlank { u.id.take(8) + "\u2026" }
        }
        val participantRows = s.participants.map { p ->
            val (label, emoji) = statusLabelEmoji(p.status)
            AttendeeRow(
                userId = p.userId,
                displayName = nameByUserId[p.userId] ?: (p.userId.take(8) + "\u2026"),
                status = p.status,
                statusLabel = label,
                statusEmoji = emoji,
            )
        }

        // ---- Имена родителей из meeting.attendees ----
        val attendeeIds = meeting?.attendees?.map { it.parentId }?.distinct() ?: emptyList()
        val attendeeUsers = if (attendeeIds.isEmpty()) emptyList()
            else userRepository.getUsersByIds(attendeeIds)
        val nameByParentId = attendeeUsers.associate { u ->
            u.id to "${u.firstName} ${u.lastName}".trim().ifBlank { u.id.take(8) + "\u2026" }
        }

        // Статус родителя: из EventParticipantEntity (истина), fallback — ParentAttendee.confirmed
        val participantByUser = s.participants.associateBy { it.userId }
        val attendeeRows = (meeting?.attendees ?: emptyList()).map { att ->
            val status = participantByUser[att.parentId]?.status
                ?: if (att.confirmed) "accepted" else "invited"
            val (label, emoji) = statusLabelEmoji(status)
            AttendeeRow(
                userId = att.parentId,
                displayName = nameByParentId[att.parentId] ?: (att.parentId.take(8) + "\u2026"),
                status = status,
                statusLabel = label,
                statusEmoji = emoji,
            )
        }

        // ---- Вопросы родителей (дедуп) ----
        val seen = mutableSetOf<String>()
        val questions = mutableListOf<ParentQuestion>()
        // источник 1: EventParticipantEntity.questions
        s.participants.forEach { p ->
            p.questions.forEach { q ->
                val key = "${p.userId}|$q"
                if (seen.add(key)) {
                    questions.add(
                        ParentQuestion(
                            parentId = p.userId,
                            parentName = nameByUserId[p.userId] ?: (p.userId.take(8) + "\u2026"),
                            text = q,
                        )
                    )
                }
            }
        }
        // источник 2: ParentMeetingEntity.attendees.questions
        (meeting?.attendees ?: emptyList()).forEach { att ->
            att.questions.forEach { q ->
                val key = "${att.parentId}|$q"
                if (seen.add(key)) {
                    questions.add(
                        ParentQuestion(
                            parentId = att.parentId,
                            parentName = nameByParentId[att.parentId] ?: (att.parentId.take(8) + "\u2026"),
                            text = q,
                        )
                    )
                }
            }
        }

        _state.update {
            it.copy(
                participantRows = participantRows,
                attendeeRows = attendeeRows,
                parentQuestions = questions,
            )
        }
    }

    private fun statusLabelEmoji(status: String): Pair<String, String> = when (status) {
        "invited"  -> "Не ответил"  to "\u23F3"
        "accepted" -> "Будет"       to "\u2705"
        "declined" -> "Не сможет"   to "\u274C"
        "attended" -> "Посетил"     to "\u2705"
        "missed"   -> "Пропущено"   to "\u26A0\uFE0F"
        else       -> status        to "\u2753"
    }

    fun acceptInvite() {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            eventRepository.updateParticipantStatus(eventId, uid, "accepted")
        }
    }

    fun declineInvite() {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            eventRepository.updateParticipantStatus(eventId, uid, "declined")
        }
    }

    fun markAttended(userId: String) {
        viewModelScope.launch {
            eventRepository.updateParticipantStatus(eventId, userId, "attended")
        }
    }

    /**
     * Запрашивает согласия у родителей всех teen-ов клуба на событие.
     * Пропускает уже созданные записи (не перезаписывает ответы).
     */
    fun requestConsents() {
        val event = _state.value.event ?: return
        val clubId = event.clubId
        val requesterId = currentUserId ?: return

        if (clubId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "У события нет клуба") }
            return
        }

        _state.update { it.copy(isRequesting = true, errorMessage = null, consentMessage = null) }

        viewModelScope.launch {
            try {
                // (f-2): согласия только если событие для teen / всех
                val tg = event.targetGroup
                val shouldRequest = tg == null || tg == "all_teens" || tg == "all_members"
                if (!shouldRequest) {
                    _state.update {
                        it.copy(
                            isRequesting = false,
                            consentMessage = "Событие не для следопытов — согласия не нужны",
                        )
                    }
                    return@launch
                }

                // Все teen-ы клуба
                val teens = userRepository.observeClubMembersByRole(clubId, "teen").first()
                if (teens.isEmpty()) {
                    _state.update {
                        it.copy(isRequesting = false, consentMessage = "В клубе нет следопытов")
                    }
                    return@launch
                }

                val toCreate = mutableListOf<EventConsentEntity>()
                var skippedExisting = 0

                for (teen in teens) {
                    // Активные родители teen-а
                    val links = try {
                        parentRepository.observeParents(teen.id).first()
                    } catch (_: Exception) { emptyList() }

                    val activeParents = links.filter { it.status == "active" }

                    for (link in activeParents) {
                        val existing = parentRepository.getConsent(
                            eventId = event.id,
                            childId = teen.id,
                            parentId = link.parentId,
                        )
                        if (existing != null) {
                            skippedExisting++
                            continue
                        }
                        toCreate.add(
                            EventConsentEntity(
                                eventId = event.id,
                                childId = teen.id,
                                parentId = link.parentId,
                                clubId = clubId,
                                status = "pending",
                                requestedBy = requesterId,
                                requestedAt = Date(),
                            )
                        )
                    }
                }

                if (toCreate.isNotEmpty()) {
                    parentRepository.upsertConsents(toCreate)
                }

                val msg = buildString {
                    append("Запрошено согласий: ${toCreate.size}")
                    if (skippedExisting > 0) append(" (уже было: $skippedExisting)")
                }
                _state.update {
                    it.copy(isRequesting = false, consentMessage = msg)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isRequesting = false, errorMessage = e.message ?: "Ошибка")
                }
            }
        }
    }

    fun clearMessages() = _state.update {
        it.copy(consentMessage = null, errorMessage = null)
    }

    /** Архивировать событие. */
    fun archiveEvent() {
        val e = _state.value.event ?: return
        viewModelScope.launch {
            try {
                eventRepository.archiveEvent(e.id)
                _state.update { it.copy(consentMessage = "Событие в архиве") }
            } catch (ex: Exception) {
                _state.update { it.copy(errorMessage = ex.message) }
            }
        }
    }

    /** Восстановить событие из архива. */
    fun restoreEvent() {
        val e = _state.value.event ?: return
        viewModelScope.launch {
            try {
                eventRepository.restoreEvent(e.id)
                _state.update { it.copy(consentMessage = "Событие восстановлено") }
            } catch (ex: Exception) {
                _state.update { it.copy(errorMessage = ex.message) }
            }
        }
    }

    /** Показать кнопку «В архив» / «Восстановить». */
    fun isArchived(): Boolean {
        return _state.value.event?.archivedAt != null
    }

    /** Payload QR события для отображения на экране. */
    fun qrPayload(): String? {
        val e = _state.value.event ?: return null
        return e.qrCode ?: com.pathfinder.hub.ui.qr.EntityQrHelper.eventPayload(e.id)
    }

    /** Имя файла QR для кэша. */
    fun qrFilename(): String? {
        val e = _state.value.event ?: return null
        return "event_${e.id}.png"
    }
}
