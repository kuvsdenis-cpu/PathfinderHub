package com.pathfinder.hub.ui.learning.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.repository.LevelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LevelWithCount(
    val level: LevelEntity,
    val requirementCount: Int,
    val sectionCount: Int
)

data class ContentManagerUiState(
    val isLoading: Boolean = true,
    val levels: List<LevelWithCount> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ContentManagerViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContentManagerUiState())
    val state: StateFlow<ContentManagerUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                // .first() берёт первый снапшот и отписывается.
                // Без .first() collect остаётся вечно, потому что Room Flow бесконечный.
                val levels = levelRepository.observeLevels().first()

                val enriched = levels
                    .sortedBy { it.order }
                    .map { lvl ->
                        val reqs = levelRepository.getRequirements(lvl.id)
                        val sections = levelRepository.getSections(lvl.id)
                        LevelWithCount(
                            level = lvl,
                            requirementCount = reqs.size,
                            sectionCount = sections.size
                        )
                    }

                _state.update {
                    it.copy(isLoading = false, levels = enriched)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }
}
