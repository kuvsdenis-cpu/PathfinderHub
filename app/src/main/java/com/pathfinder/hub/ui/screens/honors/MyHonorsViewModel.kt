package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.repository.HonorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyHonorsUiState(
    val isLoading: Boolean = true,
    val inProgress: List<HonorEntity> = emptyList(),
    val completed: List<HonorEntity> = emptyList(),
    val available: List<HonorEntity> = emptyList()
)

@HiltViewModel
class MyHonorsViewModel @Inject constructor(
    private val honorRepository: HonorRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(MyHonorsUiState())
    val state: StateFlow<MyHonorsUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                combine(
                    honorRepository.observeAllHonors(),
                    honorRepository.observeCompletions(userId)
                ) { all, completions ->
                    val completedIds = completions.map { it.honorId }.toSet()
                    val inProgress = all.filter { it.id !in completedIds }
                    val completed = all.filter { it.id in completedIds }
                    val available = all.filter { it.id !in completedIds && !it.isLocal }
                    Triple(inProgress, completed, available)
                }.collect { (inProg, comp, avail) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            inProgress = inProg,
                            completed = comp,
                            available = avail
                        )
                    }
                }
            }
        }
    }
}