package com.pathfinder.hub.ui.screens.home.conference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import com.pathfinder.hub.data.repository.InviteRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.usecase.director.CreateInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConferenceInvitesUiState(
    val isLoading: Boolean = false,
    val lastCreated: InviteEntity? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class ConferenceInvitesViewModel @Inject constructor(
    private val createInviteUseCase: CreateInviteUseCase,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ConferenceInvitesUiState())
    val state: StateFlow<ConferenceInvitesUiState> = _state.asStateFlow()

    fun createSecretaryInvite() {
        val uid = sessionManager.getUserId() ?: return
        _state.update { it.copy(isLoading = true, errorMessage = null, lastCreated = null) }
        viewModelScope.launch {
            createInviteUseCase(
                role = "conference_secretary",
                clubId = null,
                permissions = emptyList(),
            ).fold(
                onSuccess = { inv ->
                    _state.update { it.copy(isLoading = false, lastCreated = inv) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка")
                    }
                }
            )
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
