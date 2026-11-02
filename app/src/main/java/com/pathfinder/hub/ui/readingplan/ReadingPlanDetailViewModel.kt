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
class ReadingPlanDetailViewModel @Inject constructor(
    private val repo: ReadingPlanRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val scheduleId: String = savedStateHandle.get<String>("scheduleId") ?: ""
    private val userId: String? = sessionManager.getUserId()

    private val _state = MutableStateFlow(ReadingPlanDetailUiState())
    val state: StateFlow<ReadingPlanDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeScheduleById(scheduleId).collect { s ->
                _state.update { it.copy(isLoading = false, schedule = s) }
            }
        }
        viewModelScope.launch {
            repo.observeAllDays(scheduleId).collect { list ->
                _state.update { it.copy(days = list) }
            }
        }
        viewModelScope.launch {
            repo.observeCurrentDay(scheduleId, System.currentTimeMillis()).collect { d ->
                _state.update { it.copy(currentDay = d) }
            }
        }
        val uid = userId
        if (uid != null) {
            viewModelScope.launch {
                repo.observeProgress(uid).collect { list ->
                    _state.update { it.copy(progress = list.associateBy { it.dayId }) }
                }
            }
        }
    }

    fun toggleCompleted(dayId: String) {
        val uid = userId ?: return
        viewModelScope.launch {
            val done = _state.value.progress[dayId] != null
            if (done) repo.unmarkCompleted(uid, dayId)
            else repo.markCompleted(uid, dayId)
        }
    }
}
