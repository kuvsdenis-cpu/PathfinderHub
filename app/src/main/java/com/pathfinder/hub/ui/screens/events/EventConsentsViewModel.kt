package com.pathfinder.hub.ui.screens.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.parent.EventConsentEntity
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.StorageRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventConsentItem(
    val consent: EventConsentEntity,
    val parent: UserEntity?,
    val child: UserEntity?,
)

data class EventConsentsUiState(
    val isLoading: Boolean = true,
    val items: List<EventConsentItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class EventConsentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val parentRepository: ParentRepository,
    private val userRepository: UserRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""

    private val _state = MutableStateFlow(EventConsentsUiState())
    val state: StateFlow<EventConsentsUiState> = _state.asStateFlow()

    init {
        if (eventId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не указано событие") }
        } else {
            load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            parentRepository.observeConsentsByEvent(eventId).collect { list ->
                // Refresh presigned documentUrl (TTL 7 дней), если истекает
                list.forEach { c ->
                    val url = c.documentUrl ?: return@forEach
                    try {
                        val fresh = storageRepository.refreshPresignedUrl(url, ifFreshHours = 24)
                        if (fresh != null) parentRepository.upsertConsent(c.copy(documentUrl = fresh))
                    } catch (_: Exception) {}
                }
                val items = list.map { c ->
                    EventConsentItem(
                        consent = c,
                        parent = userRepository.getUser(c.parentId),
                        child = userRepository.getUser(c.childId),
                    )
                }
                _state.update { it.copy(isLoading = false, items = items) }
            }
        }
    }
}
