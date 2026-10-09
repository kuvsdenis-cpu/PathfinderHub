package com.pathfinder.hub.ui.learning.content

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity
import com.pathfinder.hub.data.repository.LevelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SectionWithCount(
    val section: LevelSectionEntity,
    val requirementCount: Int
)

data class ContentLevelUiState(
    val isLoading: Boolean = true,
    val levelName: String = "",
    val sections: List<SectionWithCount> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ContentLevelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val levelRepository: LevelRepository,
) : ViewModel() {

    val levelId: String = savedStateHandle.get<String>("levelId") ?: ""

    private val _state = MutableStateFlow(ContentLevelUiState())
    val state: StateFlow<ContentLevelUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        if (levelId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "levelId пуст") }
            return
        }
        viewModelScope.launch {
            try {
                val level = levelRepository.getLevel(levelId)
                val sections = levelRepository.getSections(levelId)
                val requirements = levelRepository.getRequirements(levelId)
                val bySection = requirements.groupBy { it.sectionId }

                _state.update {
                    it.copy(
                        isLoading = false,
                        levelName = level?.name ?: "",
                        sections = sections
                            .sortedBy { s -> s.order }
                            .map { s -> SectionWithCount(s, bySection[s.id]?.size ?: 0) }
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
