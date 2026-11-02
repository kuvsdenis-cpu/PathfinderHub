package com.pathfinder.hub.ui.bible

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.BibleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BibleBooksViewModel @Inject constructor(
    private val bibleRepository: BibleRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BibleBooksUiState())
    val state: StateFlow<BibleBooksUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            viewModelScope.launch {
                bibleRepository.observeBooks().collect { books ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            oldTestament = books.filter { b -> b.order in 1..39 },
                            newTestament = books.filter { b -> b.order in 40..66 },
                        )
                    }
                }
            }
        } else {
            viewModelScope.launch {
                bibleRepository.observeBookmarkCountsByBook(uid).collect { counts ->
                    _state.update { st ->
                        st.copy(bookmarkCounts = counts.associate { it.bookId to it.cnt })
                    }
                }
            }
            viewModelScope.launch {
                bibleRepository.observeBooks().collect { books ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            oldTestament = books.filter { b -> b.order in 1..39 },
                            newTestament = books.filter { b -> b.order in 40..66 },
                        )
                    }
                }
            }
        }
    }
}
