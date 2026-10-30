package com.pathfinder.hub.ui.screens.levels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.StorageRepository
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.repository.BookRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.ui.storage.FileUploadResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class RequirementDetailUiState(
    val isLoading: Boolean = true,
    val requirement: LevelRequirementEntity? = null,
    val status: String = "not_started",
    val comment: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),   // <-- ДОБАВЛЕНО: картинки из PDF
    val levelName: String = "",
    val sectionName: String = "",
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val userRole: String = "",
    val books: List<BookEntity> = emptyList()
)

@HiltViewModel
class RequirementDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val levelRepository: LevelRepository,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val bookRepository: BookRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val levelId: String = savedStateHandle.get<String>("levelId") ?: ""
    private val requirementId: String = savedStateHandle.get<String>("requirementId") ?: ""

    private val _state = MutableStateFlow(RequirementDetailUiState())
    val state: StateFlow<RequirementDetailUiState> = _state.asStateFlow()

    init {
        loadRequirement()
    }

    /** Перегенерировать presigned URL вложения (TTL 7 дней). */
    suspend fun refreshAttachmentUrl(url: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            storageRepository.refreshPresignedUrl(url, ifFreshHours = 24)
        }

    fun getLevelId(): String = levelId

    fun loadRequirement() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
            return
        }

        if (levelId.isBlank() || requirementId.isBlank()) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "levelId или requirementId не заданы")
            }
            return
        }

        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            val role = user?.role ?: ""
            _state.update { it.copy(userRole = role) }

            val req = levelRepository.getRequirementById(levelId, requirementId)
            if (req == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Требование не найдено") }
                return@launch
            }

            val level = levelRepository.getLevel(levelId)
            val sections = levelRepository.getSections(levelId)
            val section = sections.firstOrNull { it.id == req.sectionId }

            val progress = levelRepository.getProgressSnapshot(userId, levelId)
                .firstOrNull { it.requirementId == requirementId }

            _state.update {
                it.copy(
                    isLoading = false,
                    requirement = req,
                    status = progress?.status ?: "not_started",
                    comment = progress?.comment,
                    mediaUrls = progress?.mediaUrls ?: emptyList(),
                    imageUrls = req.imageUrls,   // <-- из PDF
                    levelName = level?.name ?: "",
                    sectionName = section?.name ?: ""
                )
            }

            val bookIds = req.bookIds.orEmpty()
            if (bookIds.isNotEmpty()) {
                val books = bookRepository.getByIds(bookIds)
                _state.update { it.copy(books = books) }
            }
        }
    }

    fun markAsCompleted() {
        val userId = sessionManager.getUserId() ?: return

        _state.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            try {
                levelRepository.upsertProgress(
                    LevelProgressEntity(
                        userId = userId,
                        levelId = levelId,
                        requirementId = requirementId,
                        status = "submitted",
                        submittedAt = Date(),
                        approvedBy = null,
                        approvedAt = null,
                        comment = null,
                        mediaUrls = _state.value.mediaUrls,
                        pendingSync = true
                    )
                )
                _state.update {
                    it.copy(
                        isSaving = false,
                        status = "submitted",
                        successMessage = "Требование отправлено на проверку"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка")
                }
            }
        }
    }

    fun resetStatus() {
        val userId = sessionManager.getUserId() ?: return

        _state.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            try {
                levelRepository.clearProgressForRequirement(userId, requirementId)
                _state.update {
                    it.copy(
                        isSaving = false,
                        status = "not_started",
                        mediaUrls = emptyList(),
                        comment = null,
                        successMessage = "Отметка сброшена"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка")
                }
            }
        }
    }

    fun attachUploadedFile(result: FileUploadResult) {
        val userId = sessionManager.getUserId() ?: return
        val newMediaUrls = (_state.value.mediaUrls + result.fileUrl).distinct()

        _state.update { it.copy(isSaving = true, successMessage = null, errorMessage = null) }

        viewModelScope.launch {
            try {
                levelRepository.upsertProgress(
                    LevelProgressEntity(
                        userId = userId,
                        levelId = levelId,
                        requirementId = requirementId,
                        status = "submitted",
                        submittedAt = Date(),
                        approvedBy = null,
                        approvedAt = null,
                        comment = "Файл: ${result.fileName}",
                        mediaUrls = newMediaUrls,
                        pendingSync = true
                    )
                )
                _state.update {
                    it.copy(
                        isSaving = false,
                        status = "submitted",
                        mediaUrls = newMediaUrls,
                        successMessage = "Файл отправлен на проверку"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка отправки")
                }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }
}