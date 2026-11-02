package com.pathfinder.hub.ui.learning.content

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.learning.HonorRequirementEntity
import com.pathfinder.hub.data.repository.HonorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContentHonorUiState(
    val isLoading: Boolean = true,
    val honorName: String = "",
    val requirements: List<HonorRequirementEntity> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ContentHonorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val honorRepository: HonorRepository,
) : ViewModel() {

    val honorId: String = savedStateHandle.get<String>("honorId") ?: ""

    private val _state = MutableStateFlow(ContentHonorUiState())
    val state: StateFlow<ContentHonorUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        if (honorId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "honorId пуст") }
            return
        }
        viewModelScope.launch {
            try {
                val honor = honorRepository.getHonor(honorId)
                val reqs = honorRepository.getRequirements(honorId)
                    .sortedBy { it.order }
                _state.update {
                    it.copy(
                        isLoading = false,
                        honorName = honor?.name ?: "",
                        requirements = reqs,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
