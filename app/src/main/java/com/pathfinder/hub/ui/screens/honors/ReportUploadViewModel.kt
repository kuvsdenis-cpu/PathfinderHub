package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.repository.HonorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ReportUploadUiState(
    val comment: String = "",
    val mediaUrls: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ReportUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val honorRepository: HonorRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val honorId: String = savedStateHandle.get<String>("honorId") ?: ""
    private val requirementId: String = savedStateHandle.get<String>("requirementId") ?: ""

    private val _state = MutableStateFlow(ReportUploadUiState())
    val state: StateFlow<ReportUploadUiState> = _state.asStateFlow()

    fun onCommentChange(v: String) = _state.update { it.copy(comment = v) }

    fun addMediaUrl(url: String) {
        _state.update { it.copy(mediaUrls = it.mediaUrls + url) }
    }

    fun removeMediaUrl(url: String) {
        _state.update { it.copy(mediaUrls = it.mediaUrls - url) }
    }

    fun submit() {
        val userId = sessionManager.getUserId() ?: return
        _state.update { it.copy(isSaving = true, successMessage = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                honorRepository.upsertProgress(
                    HonorProgressEntity(
                        userId = userId, honorId = honorId, requirementId = requirementId,
                        status = "submitted", submittedAt = Date(),
                        approvedBy = null, approvedAt = null,
                        mediaUrls = _state.value.mediaUrls,
                        comment = _state.value.comment.ifBlank { null },
                        pendingSync = true
                    )
                )
                _state.update {
                    it.copy(isSaving = false, successMessage = "Отчёт отправлен на проверку")
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
