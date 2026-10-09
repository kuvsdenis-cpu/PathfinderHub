package com.pathfinder.hub.ui.learning.content

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.repository.LevelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContentSectionUiState(
    val isLoading: Boolean = true,
    val sectionName: String = "",
    val requirements: List<LevelRequirementEntity> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ContentSectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val levelRepository: LevelRepository,
) : ViewModel() {

    val levelId: String = savedStateHandle.get<String>("levelId") ?: ""
    val sectionId: String = savedStateHandle.get<String>("sectionId") ?: ""

    private val _state = MutableStateFlow(ContentSectionUiState())
    val state: StateFlow<ContentSectionUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        if (levelId.isBlank() || sectionId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Параметры пусты") }
            return
        }
        viewModelScope.launch {
            try {
                val section = levelRepository.getSections(levelId).firstOrNull { it.id == sectionId }
                val reqs = levelRepository.getRequirementsBySection(levelId, sectionId)
                    .sortedBy { it.order }
                _state.update {
                    it.copy(
                        isLoading = false,
                        sectionName = section?.name ?: "",
                        requirements = reqs
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
