package com.pathfinder.hub.ui.qr.quests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.repository.QrQuestRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestListItem(
    val quest: QrQuestEntity,
    val stepCount: Int,
)

data class ManageQuestsUiState(
    val isLoading: Boolean = true,
    val items: List<QuestListItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ManageQuestsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrQuestRepository: QrQuestRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageQuestsUiState())
    val state: StateFlow<ManageQuestsUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            viewModelScope.launch {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                qrQuestRepository.observeAllQuests(clubId).collect { quests ->
                    val items = quests.map { q ->
                        QuestListItem(q, qrQuestRepository.getStepCount(q.id))
                    }
                    _state.update { it.copy(isLoading = false, items = items) }
                }
            }
        }
    }

    fun deleteQuest(id: String) {
        viewModelScope.launch {
            try { qrQuestRepository.deleteQuest(id) }
            catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }
}
