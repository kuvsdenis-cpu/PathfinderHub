package com.pathfinder.hub.ui.screens.home.director

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.repository.AnswerRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.usecase.learning.CompleteHonorUseCase
import com.pathfinder.hub.domain.usecase.learning.CompleteLevelUseCase
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

    data class Answer(
        val answer: RequirementTaskAnswerEntity,
        override val userName: String
    ) : ApprovalItem() {
        override val userId: String get() = answer.userId
        override val requirementId: String get() = answer.requirementId
        override val submittedAt: Date? get() = answer.submittedAt
    }
}

data class ApprovalsUiState(
    val isLoading: Boolean = true,
    val items: List<ApprovalItem> = emptyList(),
    val currentUserId: String? = null,
    val errorMessage: String? = null,
    val processingId: String? = null, // userId_requirementId для индикатора
    val completedMessage: String? = null // «Ступень завершена!» / «Специализация завершена!»
)

@HiltViewModel
class ApprovalsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val answerRepository: AnswerRepository,
    private val completeLevelUseCase: CompleteLevelUseCase,
    private val completeHonorUseCase: CompleteHonorUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "ApprovalsVM"
    }

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

                combine(
                    userRepository.observeClubMembers(clubId),
                    levelRepository.observePendingApprovalsByClub(clubId),
                    honorRepository.observePendingApprovalsByClub(clubId),
                    answerRepository.observePendingAnswers(clubId)
                ) { users, levelProgress, honorProgress, pendingAnswers ->
                    val userMap = users.associateBy { it.id }
                    val levelItems = levelProgress.mapNotNull { p ->
                        val user = userMap[p.userId] ?: return@mapNotNull null
                        ApprovalItem.Level(p, p.levelId, "${user.firstName} ${user.lastName}".trim())
                    }
                    val honorItems = honorProgress.mapNotNull { p ->
                        val user = userMap[p.userId] ?: return@mapNotNull null
                        ApprovalItem.Honor(p, p.honorId, "${user.firstName} ${user.lastName}".trim())
                    }
                    val answerItems = pendingAnswers.mapNotNull { a ->
                        val user = userMap[a.userId] ?: return@mapNotNull null
                        ApprovalItem.Answer(a, "${user.firstName} ${user.lastName}".trim())
                    }
                    (levelItems + honorItems + answerItems).sortedByDescending { it.submittedAt?.time ?: 0L }
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
                    is ApprovalItem.Answer -> {
                        if (newStatus == "approved") {
                            answerRepository.approve(item.answer, directorId).getOrThrow()
                        } else {
                            answerRepository.reject(
                                item.answer, directorId,
                                comment?.takeIf { it.isNotBlank() } ?: "Отклонено"
                            ).getOrThrow()
                        }
                    }
                }

                // После одобрения проверяем, все ли требования ступени/специализации
                // собраны. Если да — вызываем CompleteLevelUseCase/CompleteHonorUseCase,
                // которые начислят ачивки и обновят цифровую форму.
                if (newStatus == "approved") {
                    when (item) {
                        is ApprovalItem.Level -> checkLevelCompletion(item, directorId)
                        is ApprovalItem.Honor -> checkHonorCompletion(item, directorId)
                        is ApprovalItem.Answer -> Unit
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "processAction failed", e)
                _state.update {
                    it.copy(errorMessage = e.message ?: "Ошибка обработки")
                }
            } finally {
                _state.update { it.copy(processingId = null) }
            }
        }
    }

    /**
     * Проверяет, все ли требования ступени одобрены.
     * Если да — вызывает CompleteLevelUseCase (начисление ачивок + шеврон).
     */
    private suspend fun checkLevelCompletion(item: ApprovalItem.Level, approver: String) {
        try {
            val allRequirements = levelRepository.getRequirements(item.levelId)
            if (allRequirements.isEmpty()) {
                Log.w(TAG, "checkLevelCompletion: нет требований у ступени ${item.levelId}")
                return
            }

            val allProgress = levelRepository.getProgressSnapshot(item.userId, item.levelId)
            val approvedIds = allProgress
                .filter { it.status == "approved" }
                .map { it.requirementId }
                .toSet()

            val allApproved = allRequirements.all { it.id in approvedIds }

            if (allApproved) {
                Log.d(TAG, "Ступень ${item.levelId} полностью одобрена — завершаем")
                val result = completeLevelUseCase(
                    userId = item.userId,
                    levelId = item.levelId,
                    approver = approver
                )
                result.fold(
                    onSuccess = {
                        _state.update {
                            it.copy(completedMessage = "Ступень «${item.levelId}» завершена!")
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "completeLevelUseCase failed", e)
                        _state.update {
                            it.copy(errorMessage = "Ступень одобрена, но завершить не удалось: ${e.message}")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkLevelCompletion failed", e)
        }
    }

    /**
     * Проверяет, все ли требования специализации одобрены.
     * Если да — вызывает CompleteHonorUseCase (начисление ачивок + нашивка).
     */
    private suspend fun checkHonorCompletion(item: ApprovalItem.Honor, approver: String) {
        try {
            val allRequirements = honorRepository.getRequirements(item.honorId)
            if (allRequirements.isEmpty()) {
                Log.w(TAG, "checkHonorCompletion: нет требований у специализации ${item.honorId}")
                return
            }

            val allProgress = honorRepository.getProgressSnapshot(item.userId, item.honorId)
            val approvedIds = allProgress
                .filter { it.status == "approved" }
                .map { it.requirementId }
                .toSet()

            val allApproved = allRequirements.all { it.id in approvedIds }

            if (allApproved) {
                Log.d(TAG, "Специализация ${item.honorId} полностью одобрена — завершаем")
                val result = completeHonorUseCase(
                    userId = item.userId,
                    honorId = item.honorId,
                    approver = approver
                )
                result.fold(
                    onSuccess = {
                        _state.update {
                            it.copy(completedMessage = "Специализация «${item.honorId}» сдана!")
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "completeHonorUseCase failed", e)
                        _state.update {
                            it.copy(errorMessage = "Специализация одобрена, но завершить не удалось: ${e.message}")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkHonorCompletion failed", e)
        }
    }

    fun clearCompletedMessage() {
        _state.update { it.copy(completedMessage = null) }
    }
}