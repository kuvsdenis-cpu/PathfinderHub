package com.pathfinder.hub.ui.bible

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.bible.BibleBookmarkEntity
import com.pathfinder.hub.data.local.entity.bible.BibleVerseEntity
import com.pathfinder.hub.data.repository.BibleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BibleChapterViewModel @Inject constructor(
    private val bibleRepository: BibleRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""
    private val chapter: Int = savedStateHandle.get<Int>("chapter") ?: 1
    private val initialVerseId: String? = savedStateHandle.get<String>("verseId")
    private val userId: String? = sessionManager.getUserId()

    private val _state = MutableStateFlow(
        BibleChapterUiState(
            chapterNumber = chapter,
            verseId = initialVerseId,
        )
    )
    private val _notes = MutableStateFlow<Map<String, String>>(emptyMap())
    val state: StateFlow<BibleChapterUiState> = _state.asStateFlow()

    init {
        val uid = userId
        val versionId = bookId.substringBefore(":")
        if (uid == null) {
            _state.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                bibleRepository.getBook(bookId)?.let { b ->
                    _state.update {
                        it.copy(
                            bookName = b.name,
                            chapterNumber = chapter,
                            hasNextChapter = chapter < b.chaptersCount,
                        )
                    }
                }
            }
            viewModelScope.launch {
                combine(
                    bibleRepository.observeVerses(versionId, bookId, chapter),
                    bibleRepository.observeBookmarkedVerseIds(uid, bookId, chapter),
                ) { verses, bookmarked ->
                    verses to bookmarked.toSet()
                }.collect { (verses, bookmarked) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            verses = verses,
                            bookmarkedVerseIds = bookmarked,
                        )
                    }
                }
            }
            viewModelScope.launch {
                bibleRepository.observeBookmarks(uid).collect { bookmarks ->
                    val notesMap = bookmarks
                        .filter { !it.note.isNullOrBlank() }
                        .associate { it.verseId to (it.note ?: "") }
                    _notes.value = notesMap
                    _state.update { it.copy(notes = notesMap) }
                }
            }
        }
    }

    fun toggleBookmark(verse: BibleVerseEntity) {
        val uid = userId ?: return
        viewModelScope.launch {
            if (bibleRepository.isBookmarked(uid, verse.id)) {
                bibleRepository.deleteBookmark(uid, verse.id)
            } else {
                bibleRepository.upsertBookmark(
                    BibleBookmarkEntity(
                        id = "$uid:${verse.id}",
                        userId = uid,
                        verseId = verse.id,
                        versionId = verse.versionId,
                        bookId = verse.bookId,
                        chapter = verse.chapter,
                        verse = verse.verse,
                        bookName = _state.value.bookName,
                        textPreview = verse.text.take(200),
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    fun bookIdPublic(): String = bookId


    /**
     * Сбрасывает verseId после успешного скролла.
     * Вызывается из Screen после animateScrollToItem.
     * Благодаря этому LaunchedEffect(state.verseId) не перезапускается
     * при recomposition, но сработает при новом переходе из закладок.
     */
    fun consumeScrollTarget() {
        if (_state.value.verseId != null) {
            _state.update { it.copy(verseId = null) }
        }
    }

    // ---------- B6.1: заметки ----------
    fun getNote(verseId: String): String? {
        return _state.value.notes[verseId]
    }

    fun saveNote(verseId: String, note: String?) {
        val uid = userId ?: return
        viewModelScope.launch {
            bibleRepository.updateNote(uid, verseId, note)
        }
    }
}
