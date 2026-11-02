package com.pathfinder.hub.ui.bible

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.bible.BibleBookmarkEntity
import com.pathfinder.hub.data.repository.BibleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BibleBookmarksViewModel @Inject constructor(
    private val bibleRepository: BibleRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val userId: String? = sessionManager.getUserId()

    private val _state = MutableStateFlow(BibleBookmarksUiState())
    val state: StateFlow<BibleBookmarksUiState> = _state.asStateFlow()

    init {
        val uid = userId
        if (uid == null) {
            _state.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                bibleRepository.observeBookmarks(uid).collect { list ->
                    val sections = list
                        .groupBy { it.bookId }
                        .map { (bookId, items) ->
                            BookmarkSection(
                                bookId = bookId,
                                bookName = items.firstOrNull()?.bookName?.ifBlank { null }
                                    ?: bookId.substringAfter(':'),
                                bookmarks = items.sortedBy { it.chapter * 1000 + it.verse },
                            )
                        }
                        .sortedBy { it.bookId }
                    _state.update {
                        it.copy(isLoading = false, bookmarks = list, sections = sections)
                    }
                }
            }
        }
    }

    fun remove(verseId: String) {
        val uid = userId ?: return
        viewModelScope.launch {
            bibleRepository.deleteBookmark(uid, verseId)
        }
    }
}
