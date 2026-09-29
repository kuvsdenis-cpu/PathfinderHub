package com.pathfinder.hub.ui.screens.events

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class CreateEventUiState(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val dateStart: String = "", // формат "yyyy-MM-dd HH:mm"
    val dateEnd: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreated: Boolean = false
)

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateEventUiState())
    val state: StateFlow<CreateEventUiState> = _state.asStateFlow()

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun onLocationChange(value: String) = _state.update { it.copy(location = value) }
    fun onDateStartChange(value: String) = _state.update { it.copy(dateStart = value) }
    fun onDateEndChange(value: String) = _state.update { it.copy(dateEnd = value) }

    fun createEvent() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        if (s.dateStart.isBlank() || s.dateEnd.isBlank()) {
            _state.update { it.copy(errorMessage = "Укажите дату начала и окончания") }
            return
        }

        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId

                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                val dateStart = fmt.parse(s.dateStart) ?: Date()
                val dateEnd = fmt.parse(s.dateEnd) ?: Date()

                val event = EventEntity(
                    id = UUID.randomUUID().toString(),
                    level = "club",
                    clubId = clubId,
                    type = "meeting",
                    title = s.title.trim(),
                    description = s.description.trim(),
                    dateStart = dateStart,
                    dateEnd = dateEnd,
                    timezone = java.util.TimeZone.getDefault().id,
                    location = s.location.trim(),
                    isRecurring = false,
                    recurrenceRule = null,
                    parentEventId = null,
                    createdBy = userId,
                    status = "published",
                    autoModerationResult = "approved",
                    moderatedBy = userId,
                    moderatedAt = Date(),
                    reviewDeadline = null,
                    hiddenReasonCode = null,
                    hiddenReasonNote = null,
                    visibility = "club",
                    publishedAt = Date()
                )

                eventRepository.upsert(event)
                _state.update { it.copy(isLoading = false, isCreated = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка создания")
                }
            }
        }
    }
}