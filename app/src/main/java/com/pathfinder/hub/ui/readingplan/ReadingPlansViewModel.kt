package com.pathfinder.hub.ui.readingplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.repository.ReadingPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingPlansViewModel @Inject constructor(
    private val repo: ReadingPlanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReadingPlansUiState())
    val state: StateFlow<ReadingPlansUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeAllSchedules().collect { list ->
                _state.update { it.copy(isLoading = false, schedules = list) }
            }
        }
    }
}
