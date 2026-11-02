package com.pathfinder.hub.ui.pearls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.PearlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PearlsViewModel @Inject constructor(
    private val pearlRepository: PearlRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(PearlsUiState())
    val state: StateFlow<PearlsUiState> = _state.asStateFlow()

    init {
        val today = System.currentTimeMillis()
        val userId = sessionManager.getUserId()

        viewModelScope.launch {
            pearlRepository.observeActiveSchedule().collect { schedule ->
                if (schedule == null) {
                    _state.update { it.copy(isLoading = false, noSchedule = true) }
                    return@collect
                }
                _state.update {
                    it.copy(scheduleId = schedule.id, scheduleTitle = schedule.title)
                }

                launch {
                    pearlRepository.observeCurrentPearl(schedule.id, today).collect { cur ->
                        _state.update { it.copy(currentPearl = cur) }
                    }
                }
                launch {
                    pearlRepository.observeAllPearls(schedule.id).collect { pearls ->
                        val sections = pearls
                            .groupBy { it.season }
                            .toSortedMap()
                            .map { (num, list) ->
                                SeasonSection(
                                    number = num,
                                    title = list.firstOrNull()?.seasonTitle ?: "",
                                    pearls = list.sortedBy { it.weekNumber },
                                )
                            }
                        _state.update { it.copy(isLoading = false, seasons = sections) }
                    }
                }
                if (!userId.isNullOrBlank()) {
                    launch {
                        pearlRepository.observeProgress(userId).collect { list ->
                            _state.update { st -> st.copy(progress = list.associateBy { it.pearlId }) }
                        }
                    }
                }
            }
        }
    }
}
