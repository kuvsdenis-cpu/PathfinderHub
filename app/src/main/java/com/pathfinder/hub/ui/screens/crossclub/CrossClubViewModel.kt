package com.pathfinder.hub.ui.screens.crossclub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.crossclub.CrossClubInviteEntity
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.remote.PushDispatcher
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.CrossClubRepository
import com.pathfinder.hub.data.repository.EventRepository
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


data class HostInviteRow(
    val invite: CrossClubInviteEntity,
    val inviteeClub: ClubEntity?,
    val targetTitle: String,
)

data class CrossClubUiState(
    val isLoading: Boolean = true,
    val clubId: String? = null,
    val conferenceId: String? = null,
    val allClubs: List<ClubEntity> = emptyList(),
    val myEvents: List<EventEntity> = emptyList(),
    val outgoing: List<HostInviteRow> = emptyList(),
    val incoming: List<HostInviteRow> = emptyList(),
    val showCreateDialog: Boolean = false,
    val selectedInviteeClubId: String? = null,
    val selectedEventId: String? = null,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class CrossClubViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val eventRepository: EventRepository,
    private val crossClubRepository: CrossClubRepository,
    private val pushDispatcher: PushDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(CrossClubUiState())
    val state: StateFlow<CrossClubUiState> = _state.asStateFlow()

    private val currentUserId: String? = sessionManager.getUserId()

    init {
        load()
    }

    private fun load() {
        val uid = currentUserId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }
        viewModelScope.launch {
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            val confId = me?.conferenceId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            _state.update { it.copy(clubId = clubId, conferenceId = confId) }

            // Клубы конференции (кроме своего)
            if (!confId.isNullOrBlank()) {
                clubRepository.observeClubsByConference(confId).collect { list ->
                    _state.update { s ->
                        s.copy(allClubs = list.filter { it.id != clubId })
                    }
                }
            }

            // События своего клуба
            eventRepository.observeEventsInRange(
                clubId,
                System.currentTimeMillis() - 7L * 24 * 3600 * 1000,
                System.currentTimeMillis() + 365L * 24 * 3600 * 1000,
            ).collect { list ->
                _state.update { it.copy(myEvents = list) }
            }
        }

        viewModelScope.launch {
            val me = userRepository.getUser(uid) ?: return@launch
            val clubId = me.clubId ?: return@launch

            crossClubRepository.observeHostInvites(clubId).collect { list ->
                val rows = list.map { inv ->
                    HostInviteRow(
                        invite = inv,
                        inviteeClub = clubRepository.getClub(inv.inviteeClubId),
                        targetTitle = eventRepository.getEvent(inv.targetId)?.title ?: inv.targetId,
                    )
                }
                _state.update { it.copy(outgoing = rows, isLoading = false) }
            }
        }

        viewModelScope.launch {
            val me = userRepository.getUser(uid) ?: return@launch
            val clubId = me.clubId ?: return@launch

            crossClubRepository.observeInviteeInvites(clubId).collect { list ->
                val rows = list.map { inv ->
                    HostInviteRow(
                        invite = inv,
                        inviteeClub = clubRepository.getClub(inv.hostClubId),
                        targetTitle = eventRepository.getEvent(inv.targetId)?.title ?: inv.targetId,
                    )
                }
                _state.update { it.copy(incoming = rows) }
            }
        }
    }

    fun openCreateDialog() = _state.update { it.copy(showCreateDialog = true) }
    fun closeCreateDialog() = _state.update {
        it.copy(showCreateDialog = false, selectedEventId = null, selectedInviteeClubId = null)
    }

    fun selectInviteeClub(id: String) = _state.update { it.copy(selectedInviteeClubId = id) }
    fun selectEvent(id: String) = _state.update { it.copy(selectedEventId = id) }

    fun createInvite() {
        val s = _state.value
        val uid = currentUserId ?: return
        val hostClubId = s.clubId ?: return
        val inviteeClub = s.selectedInviteeClubId ?: return
        val event = s.selectedEventId ?: return

        _state.update { it.copy(isCreating = true, errorMessage = null) }
        viewModelScope.launch {
            val result = crossClubRepository.createInvite(
                hostClubId = hostClubId,
                inviteeClubId = inviteeClub,
                targetType = "event",
                targetId = event,
                createdBy = uid,
            )
            result.fold(
                onSuccess = { invite ->
                    _state.update {
                        it.copy(
                            isCreating = false,
                            showCreateDialog = false,
                            selectedEventId = null,
                            selectedInviteeClubId = null,
                            infoMessage = "Приглашение отправлено",
                        )
                    }

                    // Push директору invitee-клуба
                    viewModelScope.launch {
                        val eventTitle = eventRepository.getEvent(invite.targetId)?.title
                            ?: invite.targetId
                        val hostClub = clubRepository.getClub(invite.hostClubId)
                        val directors = try {
                            userRepository.observeClubMembersByRole(
                                    invite.inviteeClubId, "director"
                                ).first()
                        } catch (_: Exception) { emptyList() }

                        directors.forEach { d ->
                            pushDispatcher.sendToUser(
                                userId = d.id,
                                title = "Межклубное приглашение",
                                body = (hostClub?.name ?: "Клуб") + " приглашает на «" +
                                        eventTitle + "»",
                                data = mapOf(
                                    "type" to "cross_club_invite",
                                    "inviteId" to invite.id,
                                    "hostClubId" to invite.hostClubId,
                                    "targetId" to invite.targetId,
                                ),
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isCreating = false, errorMessage = e.message ?: "Ошибка")
                    }
                }
            )
        }
    }

    fun revokeInvite(id: String) {
        viewModelScope.launch {
            crossClubRepository.revokeInvite(id)
            _state.update { it.copy(infoMessage = "Приглашение отозвано") }
        }
    }

    fun acceptInvite(id: String) {
        viewModelScope.launch {
            crossClubRepository.acceptInvite(id)
            _state.update { it.copy(infoMessage = "Приглашение принято") }
        }
    }

    fun declineInvite(id: String) {
        viewModelScope.launch {
            crossClubRepository.declineInvite(id)
            _state.update { it.copy(infoMessage = "Приглашение отклонено") }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
