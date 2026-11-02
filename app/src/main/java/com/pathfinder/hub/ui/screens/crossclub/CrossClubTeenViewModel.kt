package com.pathfinder.hub.ui.screens.crossclub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.crossclub.CrossClubInviteCodeEntity
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class MyCodeRow(
    val code: CrossClubInviteCodeEntity,
    val eventTitle: String,
)

data class JoinedEvent(
    val eventId: String,
    val eventTitle: String,
    val hostClubId: String,
    val hostClubName: String?,
    val usedAt: Date,
)

data class CrossClubTeenUiState(
    val isLoading: Boolean = true,
    val clubId: String? = null,
    val myEvents: List<EventEntity> = emptyList(),
    val myCodes: List<MyCodeRow> = emptyList(),
    val joinedEvents: List<JoinedEvent> = emptyList(),
    val showCreateDialog: Boolean = false,
    val selectedEventId: String? = null,
    val isCreating: Boolean = false,
    val isRedeeming: Boolean = false,
    val enteredCode: String = "",
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class CrossClubTeenViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val eventRepository: EventRepository,
    private val crossClubRepository: CrossClubRepository,
    private val pushDispatcher: PushDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(CrossClubTeenUiState())
    val state: StateFlow<CrossClubTeenUiState> = _state.asStateFlow()

    private val currentUserId: String? = sessionManager.getUserId()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

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
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            _state.update { it.copy(clubId = clubId) }

            eventRepository.observeEventsInRange(
                clubId,
                System.currentTimeMillis() - 7L * 24 * 3600 * 1000,
                System.currentTimeMillis() + 180L * 24 * 3600 * 1000,
            ).collect { list ->
                _state.update { it.copy(myEvents = list) }
            }
        }

        // Мои созданные коды
        viewModelScope.launch {
            crossClubRepository.observeCodesByCreator(uid).collect { list ->
                val rows = list.map { code ->
                    MyCodeRow(
                        code = code,
                        eventTitle = eventRepository.getEvent(code.targetId)?.title ?: code.targetId,
                    )
                }
                _state.update { s -> s.copy(myCodes = rows, isLoading = false) }
            }
        }

        // Мои redeemed события (постоянно из БД)
        viewModelScope.launch {
            crossClubRepository.observeMyUsages(uid).collect { usages ->
                val joined = mutableListOf<JoinedEvent>()
                for (u in usages) {
                    val code = crossClubRepository.getCodeById(u.inviteCodeId) ?: continue
                    val event = try {
                        eventRepository.getEvent(code.targetId)
                    } catch (_: Exception) { null }
                    val host = try {
                        clubRepository.getClub(code.hostClubId)
                    } catch (_: Exception) { null }
                    joined.add(
                        JoinedEvent(
                            eventId = code.targetId,
                            eventTitle = event?.title ?: code.targetId,
                            hostClubId = code.hostClubId,
                            hostClubName = host?.name,
                            usedAt = u.usedAt,
                        )
                    )
                }
                _state.update { s ->
                    s.copy(joinedEvents = joined.sortedByDescending { it.usedAt })
                }
            }
        }
    }

    fun openCreateDialog() = _state.update { it.copy(showCreateDialog = true) }
    fun closeCreateDialog() = _state.update {
        it.copy(showCreateDialog = false, selectedEventId = null)
    }
    fun selectEvent(id: String) = _state.update { it.copy(selectedEventId = id) }

    fun createCode() {
        val s = _state.value
        val uid = currentUserId ?: return
        val clubId = s.clubId ?: return
        val eventId = s.selectedEventId ?: return

        _state.update { it.copy(isCreating = true, errorMessage = null) }
        viewModelScope.launch {
            crossClubRepository.createCode(
                hostClubId = clubId,
                targetType = "event",
                targetId = eventId,
                createdBy = uid,
            ).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isCreating = false,
                            showCreateDialog = false,
                            selectedEventId = null,
                            infoMessage = "Код создан. Передайте его другу.",
                        )
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

    fun revokeCode(id: String) {
        viewModelScope.launch {
            crossClubRepository.revokeCode(id)
            _state.update { it.copy(infoMessage = "Код отозван") }
        }
    }

    fun onCodeInput(v: String) = _state.update { it.copy(enteredCode = v.uppercase()) }

    fun redeemCode() {
        val s = _state.value
        val uid = currentUserId ?: return
        val clubId = s.clubId ?: return
        val code = s.enteredCode.trim()

        if (code.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите код") }
            return
        }

        _state.update { it.copy(isRedeeming = true, errorMessage = null) }
        viewModelScope.launch {
            crossClubRepository.redeemCode(code, uid, clubId).fold(
                onSuccess = { entity ->
                    // Push создателю кода
                    viewModelScope.launch {
                        val me = userRepository.getUser(uid)
                        val myName = "${me?.firstName ?: ""} ${me?.lastName ?: ""}".trim()
                            .ifBlank { "Следопыт" }
                        val eventTitle = entity.targetId
                        pushDispatcher.sendToUser(
                            userId = entity.createdBy,
                            title = "Друг присоединился",
                            body = myName + " присоединился к вашему событию",
                            data = mapOf(
                                "type" to "cross_club_redeem",
                                "codeId" to entity.id,
                                "targetId" to entity.targetId,
                            ),
                        )
                    }

                    // Забираем event из Firestore (чужой клуб) и кэшируем локально
                    val eventFetched = fetchEventFromFirestore(entity.hostClubId, entity.targetId)
                    if (eventFetched != null) {
                        try {
                            eventRepository.cacheEventLocally(eventFetched)
                        } catch (_: Exception) { }
                    }
                    val host = try { clubRepository.getClub(entity.hostClubId) } catch (_: Exception) { null }
                    val title = eventFetched?.title ?: entity.targetId

                    _state.update {
                        it.copy(
                            isRedeeming = false,
                            enteredCode = "",
                            infoMessage = "Присоединились к событию: $title",
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isRedeeming = false, errorMessage = e.message ?: "Ошибка")
                    }
                }
            )
        }
    }

    /**
     * Запрос Firestore напрямую — читаем событие чужого клуба.
     * Правила allow read: if isSignedIn() — разрешают.
     */
    private suspend fun fetchEventFromFirestore(hostClubId: String, eventId: String): EventEntity? {
        return try {
            val snap = firestore.collection("clubs").document(hostClubId)
                .collection("events").document(eventId)
                .get()
                .await()
            if (!snap.exists()) null
            else {
                val d = snap.data ?: return null
                EventEntity(
                    id = snap.id,
                    level = (d["level"] as? String) ?: "club",
                    clubId = (d["clubId"] as? String) ?: hostClubId,
                    type = (d["type"] as? String) ?: "meeting",
                    title = (d["title"] as? String) ?: "",
                    description = (d["description"] as? String) ?: "",
                    dateStart = parseDate(d["dateStart"]) ?: Date(),
                    dateEnd = parseDate(d["dateEnd"]) ?: Date(),
                    timezone = (d["timezone"] as? String) ?: "UTC",
                    location = (d["location"] as? String) ?: "",
                    isRecurring = (d["isRecurring"] as? Boolean) ?: false,
                    recurrenceRule = d["recurrenceRule"] as? String,
                    parentEventId = d["parentEventId"] as? String,
                    createdBy = (d["createdBy"] as? String) ?: "",
                    status = (d["status"] as? String) ?: "published",
                    autoModerationResult = (d["autoModerationResult"] as? String) ?: "approved",
                    moderatedBy = d["moderatedBy"] as? String,
                    moderatedAt = parseDate(d["moderatedAt"]),
                    reviewDeadline = parseDate(d["reviewDeadline"]),
                    hiddenReasonCode = d["hiddenReasonCode"] as? String,
                    hiddenReasonNote = d["hiddenReasonNote"] as? String,
                    visibility = (d["visibility"] as? String) ?: "club",
                    publishedAt = parseDate(d["publishedAt"]),
                    pendingSync = false,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDate(v: Any?): Date? = when (v) {
        null -> null
        is Date -> v
        is com.google.firebase.Timestamp -> v.toDate()
        is Long -> Date(v)
        else -> null
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
