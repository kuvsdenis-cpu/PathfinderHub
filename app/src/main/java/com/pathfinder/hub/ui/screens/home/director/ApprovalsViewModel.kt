package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

// === Модель заявки ===
sealed class ApprovalItem {
    abstract val userId: String
    abstract val requirementId: String
    abstract val submittedAt: Date?
    abstract val userName: String

    data class Level(
        val progress: LevelProgressEntity,
        val levelId: String,
        override val userName: String
    ) : ApprovalItem() {
        override val userId: String get() = progress.userId
        override val requirementId: String get() = progress.requirementId
        override val submittedAt: Date? get() = progress.submittedAt
    }

    data class Honor(
        val progress: HonorProgressEntity,
        val honorId: String,
        override val userName: String
    ) : ApprovalItem() {
        override val userId: String get() = progress.userId
        override val requirementId: String get() = progress.requirementId
        override val submittedAt: Date? get() = progress.submittedAt
    }
}

data class ApprovalsUiState(
    val isLoading: Boolean = true,
    val items: List<ApprovalItem> = emptyList(),
    val currentUserId: String? = null,
    val errorMessage: String? = null,
    val processingId: String? = null // userId_requirementId для индикатора
)

@HiltViewModel
class ApprovalsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ApprovalsUiState())
    val state: StateFlow<ApprovalsUiState> = _state.asStateFlow()

    init {
        loadApprovals()
    }

    private fun loadApprovals() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не авторизован") }
            return
        }

        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId

                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }

                _state.update { it.copy(isLoading = false, currentUserId = userId) }

                // Объединяем 3 потока: пользователи клуба + заявки по ступеням + заявки по специализациям
                combine(
                    userRepository.observeClubMembers(clubId),
                    levelRepository.observePendingApprovalsByClub(clubId),
                    honorRepository.observePendingApprovalsByClub(clubId)
                ) { users, levelProgress, honorProgress ->
                    val userMap = users.associateBy { it.id }
                    val levelItems = levelProgress.mapNotNull { p ->
                        val user = userMap[p.userId] ?: return@mapNotNull null
                        ApprovalItem.Level(p, p.levelId, "${user.firstName} ${user.lastName}".trim())
                    }
                    val honorItems = honorProgress.mapNotNull { p ->
                        val user = userMap[p.userId] ?: return@mapNotNull null
                        ApprovalItem.Honor(p, p.honorId, "${user.firstName} ${user.lastName}".trim())
                    }
                    (levelItems + honorItems).sortedByDescending { it.submittedAt?.time ?: 0L }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                    .collect { items ->
                        _state.update { it.copy(items = items) }
                    }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    fun approve(item: ApprovalItem) {
        processAction(item, "approved")
    }

    fun reject(item: ApprovalItem, comment: String) {
        processAction(item, "rejected", comment)
    }

    private fun processAction(item: ApprovalItem, newStatus: String, comment: String? = null) {
        val directorId = _state.value.currentUserId ?: return
        val key = "${item.userId}_${item.requirementId}"

        _state.update { it.copy(processingId = key) }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                when (item) {
                    is ApprovalItem.Level -> {
                        levelRepository.approveRequirement(
                            userId = item.userId,
                            requirementId = item.requirementId,
                            status = newStatus,
                            approvedBy = directorId,
                            at = now
                        )
                    }
                    is ApprovalItem.Honor -> {
                        honorRepository.approveRequirement(
                            userId = item.userId,
                            requirementId = item.requirementId,
                            status = newStatus,
                            approvedBy = directorId,
                            at = now
                        )
                    }
                }
                // После одобрения заявка автоматически исчезнет из списка (Flow реактивный)
            } catch (e: Exception) {
                _state.update {
                    it.copy(errorMessage = e.message ?: "Ошибка обработки")
                }
            } finally {
                _state.update { it.copy(processingId = null) }
            }
        }
    }
}