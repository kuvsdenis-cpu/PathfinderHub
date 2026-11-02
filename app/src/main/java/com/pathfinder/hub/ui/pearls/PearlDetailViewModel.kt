package com.pathfinder.hub.ui.pearls

import androidx.lifecycle.SavedStateHandle
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
class PearlDetailViewModel @Inject constructor(
    private val pearlRepository: PearlRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pearlId: String = savedStateHandle.get<String>("pearlId") ?: ""
    private val userId: String? = sessionManager.getUserId()

    private val _state = MutableStateFlow(PearlDetailUiState())
    val state: StateFlow<PearlDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            pearlRepository.observePearlById(pearlId).collect { pearl ->
                _state.update { it.copy(isLoading = false, pearl = pearl) }
            }
        }
        val uid = userId
        if (uid != null && pearlId.isNotEmpty()) {
            viewModelScope.launch {
                pearlRepository.observeProgressFor(uid, pearlId).collect { p ->
                    _state.update { it.copy(progress = p) }
                }
            }
        }
    }

    fun toggleMemorized() {
        val uid = userId ?: return
        if (pearlId.isEmpty()) return
        viewModelScope.launch {
            val memorized = _state.value.progress?.memorized == true
            if (memorized) {
                pearlRepository.unmarkMemorized(uid, pearlId)
            } else {
                pearlRepository.markMemorized(uid, pearlId)
            }
        }
    }
}
