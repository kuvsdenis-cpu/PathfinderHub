package com.pathfinder.hub.ui.screens.appeals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.AppealEntity
import com.pathfinder.hub.data.repository.AppealRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppealsUiState(
    val isLoading: Boolean = true,
    val appeals: List<AppealEntity> = emptyList(),
    val userNames: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
)

@HiltViewModel
class AppealsViewModel @Inject constructor(
    private val appealRepository: AppealRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AppealsUiState())
    val state: StateFlow<AppealsUiState> = _state.asStateFlow()

    private val currentUserId: String? = sessionManager.getUserId()

    init {
        val uid = currentUserId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
        } else {
            loadAppeals(uid)
        }
    }

    private fun loadAppeals(uid: String) {
        viewModelScope.launch {
            val user = userRepository.getUser(uid)
            val flow = when (user?.role) {
                "director" -> {
                    val clubId = user.clubId ?: user.clubIds.firstOrNull()
                    if (clubId.isNullOrBlank()) {
                        _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                        return@launch
                    }
                    appealRepository.observePendingAppealsByClub(clubId)
                }
                "conference_admin", "conference_secretary" -> appealRepository.observePendingAppeals()
                else -> appealRepository.observePendingAppeals()
            }
            flow.collect { appeals ->
                val userIds = appeals.map { it.authorId }.distinct()
                val names = if (userIds.isEmpty()) emptyMap()
                else userRepository.getUsersByIds(userIds)
                    .associate { u ->
                        u.id to "${u.firstName} ${u.lastName}".trim().ifBlank { u.id.take(8) }
                    }
                _state.update {
                    it.copy(appeals = appeals, userNames = names, isLoading = false)
                }
            }
        }
    }

    fun approve(appealId: String) = resolve(appealId, "approved")
    fun reject(appealId: String) = resolve(appealId, "rejected")

    private fun resolve(appealId: String, status: String) {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            try {
                appealRepository.resolve(appealId, status, uid)
                _state.update { it.copy(errorMessage = null) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка") }
            }
        }
    }
}
