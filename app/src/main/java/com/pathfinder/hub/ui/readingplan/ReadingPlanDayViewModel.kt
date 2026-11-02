package com.pathfinder.hub.ui.readingplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.ReadingPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingPlanDayViewModel @Inject constructor(
    private val repo: ReadingPlanRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val dayId: String = savedStateHandle.get<String>("dayId") ?: ""
    private val userId: String? = sessionManager.getUserId()

    private val _state = MutableStateFlow(ReadingPlanDayUiState())
    val state: StateFlow<ReadingPlanDayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeDayById(dayId).collect { d ->
                _state.update { it.copy(isLoading = false, day = d) }
            }
        }
        val uid = userId
        if (uid != null && dayId.isNotEmpty()) {
            viewModelScope.launch {
                repo.observeProgressFor(uid, dayId).collect { p ->
                    _state.update { it.copy(progress = p) }
                }
            }
        }
    }

    fun toggleCompleted() {
        val uid = userId ?: return
        if (dayId.isEmpty()) return
        viewModelScope.launch {
            if (_state.value.progress != null) repo.unmarkCompleted(uid, dayId)
            else repo.markCompleted(uid, dayId)
        }
    }
}
