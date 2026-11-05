package com.pathfinder.hub.ui.screens.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.planning.ModerationReasonEntity
import com.pathfinder.hub.data.repository.ModerationReasonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModerationReasonsUiState(
    val isLoading: Boolean = true,
    val reasons: List<ModerationReasonEntity> = emptyList(),
    val isCreating: Boolean = false,
    val editing: ModerationReasonEntity? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ModerationReasonsViewModel @Inject constructor(
    private val repository: ModerationReasonRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ModerationReasonsUiState())
    val state: StateFlow<ModerationReasonsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureSeed() }
        viewModelScope.launch {
            repository.observeAll().collect { list ->
                _state.update { it.copy(reasons = list, isLoading = false) }
            }
        }
    }

    fun startCreate() = _state.update {
        it.copy(isCreating = true, editing = null, errorMessage = null)
    }

    fun startEdit(reason: ModerationReasonEntity) = _state.update {
        it.copy(isCreating = false, editing = reason, errorMessage = null)
    }

    fun closeDialog() = _state.update {
        it.copy(isCreating = false, editing = null, errorMessage = null)
    }

    fun save(code: String, title: String, description: String) {
        val trimmedCode = code.trim().lowercase()
        val trimmedTitle = title.trim()
        val trimmedDesc = description.trim()
        if (trimmedTitle.isBlank()) {
            _state.update { it.copy(errorMessage = "Название обязательно") }
            return
        }
        if (_state.value.isCreating) {
            if (trimmedCode.isBlank()) {
                _state.update { it.copy(errorMessage = "Код обязателен") }
                return
            }
            if (!trimmedCode.matches(Regex("[a-z0-9_]+"))) {
                _state.update { it.copy(errorMessage = "Код: только a-z, 0-9, _") }
                return
            }
        }
        viewModelScope.launch {
            try {
                val current = _state.value.editing
                if (current != null) {
                    repository.upsert(current.copy(title = trimmedTitle, description = trimmedDesc))
                } else {
                    if (repository.getByCode(trimmedCode) != null) {
                        _state.update { it.copy(errorMessage = "Код уже существует") }
                        return@launch
                    }
                    val maxOrder = _state.value.reasons.maxOfOrNull { it.sortOrder } ?: 0
                    repository.upsert(
                        ModerationReasonEntity(
                            code = trimmedCode,
                            title = trimmedTitle,
                            description = trimmedDesc,
                            isActive = true,
                            sortOrder = maxOrder + 1,
                            isSystem = false,
                        )
                    )
                }
                closeDialog()
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка") }
            }
        }
    }

    fun toggleActive(reason: ModerationReasonEntity) {
        viewModelScope.launch {
            try { repository.setActive(reason.code, !reason.isActive) }
            catch (e: Exception) { _state.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun delete(reason: ModerationReasonEntity) {
        if (reason.isSystem) {
            _state.update { it.copy(errorMessage = "Системную причину нельзя удалить") }
            return
        }
        viewModelScope.launch {
            try {
                if (!repository.deleteCustom(reason.code)) {
                    _state.update { it.copy(errorMessage = "Не удалось удалить") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }
}
