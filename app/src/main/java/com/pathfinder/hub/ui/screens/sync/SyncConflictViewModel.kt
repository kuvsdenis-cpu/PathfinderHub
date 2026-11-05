package com.pathfinder.hub.ui.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.offline.SyncConflictEntity
import com.pathfinder.hub.data.repository.SyncConflictRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncConflictUiState(
    val isLoading: Boolean = true,
    val conflicts: List<SyncConflictEntity> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class SyncConflictViewModel @Inject constructor(
    private val repository: SyncConflictRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncConflictUiState())
    val state: StateFlow<SyncConflictUiState> = _state.asStateFlow()

    private val userId: String? = sessionManager.getUserId()

    init {
        val uid = userId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
        } else {
            viewModelScope.launch {
                repository.observeUnresolved(uid).collect { list ->
                    _state.update { it.copy(conflicts = list, isLoading = false) }
                }
            }
        }
    }

    fun resolve(id: String, chooseLocal: Boolean) {
        val uid = userId ?: return
        viewModelScope.launch {
            try {
                repository.resolve(id = id, resolvedBy = uid, chooseLocal = chooseLocal)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }
}
