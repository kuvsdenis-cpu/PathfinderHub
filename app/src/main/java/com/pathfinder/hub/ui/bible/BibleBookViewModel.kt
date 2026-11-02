package com.pathfinder.hub.ui.bible

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.repository.BibleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BibleBookViewModel @Inject constructor(
    private val bibleRepository: BibleRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    private val _state = MutableStateFlow(BibleBookUiState())
    val state: StateFlow<BibleBookUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val book = bibleRepository.getBook(bookId)
            _state.update { it.copy(isLoading = false, book = book) }
        }
    }
}
