package com.pathfinder.hub.ui.sabbath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.repository.SabbathSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SabbathSchoolListUiState(
    val isLoading: Boolean = true,
    val year: String = "B",
    val quarter: Int = 4,
    val currentLesson: Int = 1,
)

@HiltViewModel
class SabbathSchoolListViewModel @Inject constructor(
    val settingsRepo: SabbathSettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SabbathSchoolListUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect { s ->
                val cur = settingsRepo.currentLesson()
                _state.update {
                    it.copy(
                        isLoading = false,
                        year = s.year,
                        quarter = s.quarter,
                        currentLesson = cur,
                    )
                }
            }
        }
    }
}
