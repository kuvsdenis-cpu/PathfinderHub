package com.pathfinder.hub.ui.sabbath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.repository.SabbathSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SabbathSchoolSettingsUiState(
    val isLoading: Boolean = true,
    val year: String = "B",
    val quarter: Int = 4,
    val autoAdvance: Boolean = true,
    val manualLesson: Int? = null,
    val currentLesson: Int = 1,
)

@HiltViewModel
class SabbathSchoolSettingsViewModel @Inject constructor(
    private val repo: SabbathSettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SabbathSchoolSettingsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.settings.collect { s ->
                val cur = repo.currentLesson()
                _state.update {
                    it.copy(
                        isLoading = false,
                        year = s.year,
                        quarter = s.quarter,
                        autoAdvance = s.autoAdvance,
                        manualLesson = s.manualLessonOverride,
                        currentLesson = cur,
                    )
                }
            }
        }
    }

    fun setYear(y: String) = viewModelScope.launch { repo.setYear(y) }
    fun setQuarter(q: Int) = viewModelScope.launch { repo.setQuarter(q) }
    fun setAutoAdvance(b: Boolean) = viewModelScope.launch { repo.setAutoAdvance(b) }
    fun setManualLesson(n: Int?) = viewModelScope.launch { repo.setManualLesson(n) }
}
