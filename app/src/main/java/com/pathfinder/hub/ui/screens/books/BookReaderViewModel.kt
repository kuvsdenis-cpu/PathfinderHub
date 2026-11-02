package com.pathfinder.hub.ui.screens.books

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.bible.BibleBookmarkEntity
import com.pathfinder.hub.data.repository.BibleRepository
import com.pathfinder.hub.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class BookChapter(val title: String = "", val paragraphs: List<String> = emptyList())
data class BookContent(val id: String = "", val title: String = "", val author: String = "", val chapters: List<BookChapter> = emptyList())
data class BookReaderUiState(
    val isLoading: Boolean = true,
    val content: BookContent? = null,
    val error: String? = null,
    val bookmarks: List<BibleBookmarkEntity> = emptyList(),
    val notes: Map<String, String> = emptyMap(),
    val scrollTarget: Pair<Int, Int>? = null,
)

@HiltViewModel
class BookReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val bibleRepository: BibleRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""
    private val userId: String? = sessionManager.getUserId()

    private val _state = MutableStateFlow(BookReaderUiState())
    val state: StateFlow<BookReaderUiState> = _state.asStateFlow()

    init { loadBook(); observeBookmarks() }

    private fun loadBook() {
        viewModelScope.launch {
            try {
                val book = withContext(Dispatchers.IO) { bookRepository.getById(bookId) }
                if (book == null) {
                    _state.update { it.copy(isLoading = false, error = "Книга не найдена") }
                    return@launch
                }
                val plainText = withContext(Dispatchers.IO) { readOrDownload(book) }
                if (plainText == null) {
                    _state.update { it.copy(isLoading = false, error = "Файл книги не найден. Удалите книгу и загрузите заново.") }
                    return@launch
                }
                val content = withContext(Dispatchers.Default) { buildContent(book.title, book.author, plainText) }
                _state.update { it.copy(isLoading = false, content = content) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки") }
            }
        }
    }

    private suspend fun readOrDownload(book: com.pathfinder.hub.data.local.entity.learning.BookEntity): String? {
        val localPath = book.contentPath
        if (!localPath.isNullOrBlank()) {
            val local = File(context.filesDir, localPath)
            if (local.exists()) return local.readText()
        }

        // 1. Новый ключ: clubs/{clubId}/books/{slug}_{id8}.txt
        val withUrl = try {
            bookRepository.ensureContentUrl(book)
        } catch (_: Exception) { book }
        val newUrl = withUrl.contentUrl
        if (newUrl != null) {
            val text = try { downloadText(newUrl) } catch (_: Exception) { null }
            if (text != null) {
                val file = File(context.filesDir, "books/${withUrl.id}.txt")
                file.parentFile?.mkdirs()
                file.writeText(text)
                bookRepository.upsert(withUrl.copy(contentPath = "books/${withUrl.id}.txt"))
                return text
            }
        }

        // 2. Fallback: старый ключ clubs/{clubId}/books/{uuid}.txt
        val legacyUrl = try { bookRepository.legacyContentUrl(book) } catch (_: Exception) { null }
        if (legacyUrl != null) {
            val text = try { downloadText(legacyUrl) } catch (_: Exception) { null }
            if (text != null) {
                val file = File(context.filesDir, "books/${book.id}.txt")
                file.parentFile?.mkdirs()
                file.writeText(text)
                bookRepository.upsert(book.copy(
                    contentUrl = legacyUrl,
                    contentPath = "books/${book.id}.txt",
                ))
                return text
            }
        }
        return null
    }

    private fun downloadText(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.requestMethod = "GET"
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { conn.disconnect() }
    }

    private fun buildContent(title: String, author: String, text: String): BookContent {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val paragraphs = normalized.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotBlank() }
        return BookContent(id = bookId, title = title, author = author,
            chapters = listOf(BookChapter(title = "", paragraphs = paragraphs)))
    }

    private fun observeBookmarks() {
        val uid = userId ?: return
        viewModelScope.launch {
            bibleRepository.observeBookmarks(uid).collect { all ->
                val forThisBook = all.filter { it.bookId == bookId }
                val notesMap = forThisBook.filter { !it.note.isNullOrBlank() }
                    .associate { paragraphKey(it.chapter, it.verse) to (it.note ?: "") }
                _state.update { it.copy(bookmarks = forThisBook, notes = notesMap) }
            }
        }
    }

    fun paragraphKey(c: Int, p: Int): String = "$bookId:$c:$p"
    fun isBookmarked(c: Int, p: Int): Boolean = _state.value.bookmarks.any { it.verseId == paragraphKey(c, p) }

    fun toggleBookmark(c: Int, p: Int, chapterTitle: String, text: String) {
        val uid = userId ?: return
        val key = paragraphKey(c, p)
        viewModelScope.launch {
            val existing = _state.value.bookmarks.firstOrNull { it.verseId == key }
            if (existing != null) bibleRepository.deleteBookmark(uid, key)
            else bibleRepository.upsertBookmark(
                BibleBookmarkEntity(id = "$uid:$key", userId = uid, verseId = key, versionId = "",
                    bookId = bookId, bookName = _state.value.content?.title ?: "", chapter = c, verse = p,
                    textPreview = text.take(200), note = null, createdAt = System.currentTimeMillis(), pendingSync = true)
            )
        }
    }

    fun saveNote(c: Int, p: Int, note: String?) {
        val uid = userId ?: return
        val key = paragraphKey(c, p)
        viewModelScope.launch {
            if (_state.value.bookmarks.any { it.verseId == key }) bibleRepository.updateNote(uid, key, note)
        }
    }

    fun getNote(c: Int, p: Int): String? = _state.value.notes[paragraphKey(c, p)]
    fun scrollTo(c: Int, p: Int) { _state.update { it.copy(scrollTarget = c to p) } }
    fun consumeScrollTarget() { if (_state.value.scrollTarget != null) _state.update { it.copy(scrollTarget = null) } }

    /** Удалить текущую книгу (soft delete + Firestore). */
    fun deleteBook(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val book = withContext(Dispatchers.IO) { bookRepository.getById(bookId) }
                if (book != null) {
                    bookRepository.deleteClubBook(book)
                }
                onDeleted()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка удаления") }
            }
        }
    }

    fun shareParagraph(c: Int, p: Int): String {
        val content = _state.value.content ?: return ""
        val ch = content.chapters.getOrNull(c) ?: return ""
        val t = ch.paragraphs.getOrNull(p) ?: return ""
        return "«${content.title}», ${ch.title}\n\n$t"
    }

    fun shareChapter(c: Int): String {
        val content = _state.value.content ?: return ""
        val ch = content.chapters.getOrNull(c) ?: return ""
        return buildString {
            append("«").append(content.title).append("», ").append(ch.title).append("\n\n")
            ch.paragraphs.forEachIndexed { i, p -> append(i + 1).append(". ").append(p).append("\n\n") }
        }.trim()
    }
}
