package com.pathfinder.hub.ui.learning

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import com.pathfinder.hub.data.repository.RequirementContentRepository
import com.pathfinder.hub.data.repository.TaskAnswerRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.usecase.learning.SubmitTaskAnswerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние секции «Материалы и задания» на экране требования.
 */
data class RequirementContentUiState(
    val isLoading: Boolean = true,
    val materials: List<RequirementMaterialEntity> = emptyList(),
    val tasks: List<RequirementTaskEntity> = emptyList(),
    val answers: Map<String, RequirementTaskAnswerEntity> = emptyMap(),
    val currentUserId: String = "",
    val currentClubId: String = "",
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    /** Сколько обязательных заданий выполнено (approved/auto_approved). */
    val completedRequiredCount: Int
        get() = tasks.count { t ->
            t.isRequired && answers[t.id]?.let {
                it.status in RequirementTaskAnswerEntity.DONE_STATUSES
            } == true
        }

    val requiredCount: Int get() = tasks.count { it.isRequired }

    val allRequiredDone: Boolean
        get() = requiredCount == 0 || completedRequiredCount >= requiredCount

    val hasRequiredTasks: Boolean get() = requiredCount > 0
}

@HiltViewModel
class RequirementContentViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val contentRepo: RequirementContentRepository,
    private val answerRepo: TaskAnswerRepository,
    private val submitUseCase: SubmitTaskAnswerUseCase,
) : ViewModel() {

    private val levelId: String = savedStateHandle.get<String>("levelId") ?: ""
    private val requirementId: String = savedStateHandle.get<String>("requirementId") ?: ""

    private val _state = MutableStateFlow(RequirementContentUiState())
    val state: StateFlow<RequirementContentUiState> = _state.asStateFlow()

    private var clubId: String? = null
    private var userId: String? = null

    init {
        load()
    }

    private fun load() {
        val uid = sessionManager.getUserId()
        if (uid == null || requirementId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет данных требования") }
            return
        }
        userId = uid

        viewModelScope.launch {
            val user = userRepository.getUser(uid)
            val cid = user?.clubId
            if (cid.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            clubId = cid
            _state.update { it.copy(currentUserId = uid, currentClubId = cid) }

            // Материалы
            clubId = cid

            // Прогрев Room перед observe*: listener'ы живут только пока экран открыт,
            // pull нужен для офлайн-кэша и «второго устройства».
            launch {
                try {
                    contentRepo.pullMaterials(cid, requirementId)
                    contentRepo.pullTasks(cid, requirementId)
                } catch (e: Exception) {
                    Log.w(TAG, "pull content failed", e)
                }
            }
            launch {
                try {
                    answerRepo.pullAnswers(cid)
                } catch (e: Exception) {
                    Log.w(TAG, "pull answers failed", e)
                }
            }

            // Материалы
            launch {
                contentRepo.observeMaterials(cid, requirementId)
                    .catch { e -> Log.e(TAG, "materials flow error", e) }
                    .collect { list ->
                        _state.update { it.copy(materials = list, isLoading = false) }
                    }
            }

            // Задания
            launch {
                contentRepo.observeTasks(cid, requirementId)
                    .catch { e -> Log.e(TAG, "tasks flow error", e) }
                    .collect { list ->
                        _state.update { it.copy(tasks = list.sortedBy { t -> t.orderIndex }) }
                    }
            }

            // Ответы
            launch {
                answerRepo.observeMyAnswers(cid, uid)
                    .catch { e -> Log.e(TAG, "answers flow error", e) }
                    .collect { list ->
                        _state.update { s ->
                            s.copy(answers = list.associateBy { it.taskId })
                        }
                    }
            }
        }
    }

    // ==================== ОТВЕТЫ ====================

    fun saveDraft(task: RequirementTaskEntity, answer: String, attachments: List<String>) {
        val cid = clubId ?: return
        val uid = userId ?: return
        viewModelScope.launch {
            val r = answerRepo.saveDraft(
                clubId = cid, userId = uid, requirementId = requirementId,
                taskId = task.id, answer = answer, attachments = attachments
            )
            if (r.isSuccess) {
                _state.update { it.copy(infoMessage = "Черновик сохранён") }
            } else {
                _state.update { it.copy(errorMessage = r.exceptionOrNull()?.message ?: "Ошибка сохранения") }
            }
        }
    }

    fun submit(task: RequirementTaskEntity, answer: String, attachments: List<String>) {
        val cid = clubId ?: return
        val uid = userId ?: return
        viewModelScope.launch {
            val r = submitUseCase(
                clubId = cid, userId = uid, requirementId = requirementId,
                taskId = task.id, answer = answer, attachments = attachments,
                autoCheck = task.autoCheck, correctAnswer = task.correctAnswer
            )
            r.fold(
                onSuccess = { entity ->
                    val msg = if (entity.status == RequirementTaskAnswerEntity.STATUS_AUTO_APPROVED)
                        "Ответ верный, задание зачтено"
                    else
                        "Отправлено на проверку"
                    _state.update { it.copy(infoMessage = msg) }
                },
                onFailure = { e ->
                    _state.update { it.copy(errorMessage = e.message ?: "Ошибка отправки") }
                }
            )
        }
    }

    /**
     * Прикрепляет загруженный файл к ответу задания.
     * Создаёт/обновляет ответ со статусом draft (если ещё не было).
     */
    fun attachFileToTask(taskId: String, fileUrl: String) {
        val cid = clubId ?: return
        val uid = userId ?: return
        val existing = _state.value.answers[taskId]
        val atts = (existing?.attachments ?: emptyList()) + fileUrl
        val answer = existing?.answer ?: ""

        viewModelScope.launch {
            val r = answerRepo.saveDraft(
                clubId = cid, userId = uid, requirementId = requirementId,
                taskId = taskId, answer = answer, attachments = atts
            )
            r.fold(
                onSuccess = { _state.update { it.copy(infoMessage = "Файл прикреплён") } },
                onFailure = { e -> _state.update { it.copy(errorMessage = e.message ?: "Ошибка") } }
            )
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    companion object {
        private const val TAG = "ReqContentVM"
    }
}
