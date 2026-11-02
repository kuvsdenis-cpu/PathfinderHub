package com.pathfinder.hub.ui.bible

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.bible.BibleVerseEntity
import com.pathfinder.hub.data.repository.BibleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BibleSearchUiState(
    val query: String = "",
    val results: List<BibleVerseEntity> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
)

@HiltViewModel
class BibleSearchViewModel @Inject constructor(
    private val bibleRepository: BibleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BibleSearchUiState())
    val state: StateFlow<BibleSearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.length < 2) {
            _state.update { it.copy(results = emptyList(), hasSearched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(isSearching = true) }
            try {
                val results = bibleRepository.searchVerses(q.trim(), 200)
                _state.update { it.copy(results = results, isSearching = false, hasSearched = true) }
            } catch (e: Exception) {
                _state.update { it.copy(results = emptyList(), isSearching = false, hasSearched = true) }
            }
        }
    }
}
