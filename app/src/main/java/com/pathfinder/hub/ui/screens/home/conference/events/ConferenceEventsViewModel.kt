package com.pathfinder.hub.ui.screens.home.conference.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.conference.ConferenceEventEntity
import com.pathfinder.hub.data.repository.ConferenceEventRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConferenceEventsUiState(
    val isLoading: Boolean = true,
    val items: List<ConferenceEventEntity> = emptyList(),
    val isAdminOrSecretary: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ConferenceEventsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val repo: ConferenceEventRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConferenceEventsUiState())
    val state: StateFlow<ConferenceEventsUiState> = _state.asStateFlow()

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
            val isStaff = me.role == "conference_admin" || me.role == "conference_secretary"
            _state.update { it.copy(isAdminOrSecretary = isStaff) }

            // Админ/секретарь видит все, остальные — только published
            val flow = if (isStaff) repo.observeAll(confId) else repo.observePublished(confId)
            flow.collect { list ->
                _state.update { it.copy(items = list, isLoading = false) }
            }
        }
    }

    fun cancel(event: ConferenceEventEntity) {
        viewModelScope.launch {
            try {
                repo.upsert(
                    event.copy(
                        status = "cancelled",
                        cancelledReason = "Отменено",
                        updatedAt = java.util.Date(),
                    )
                )
                _state.update { it.copy(infoMessage = "Мероприятие отменено") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repo.delete(id)
                _state.update { it.copy(infoMessage = "Удалено") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(infoMessage = null, errorMessage = null) }
}
