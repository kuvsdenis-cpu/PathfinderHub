package com.pathfinder.hub.ui.screens.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.parent.EventConsentEntity
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
            }
        }
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
