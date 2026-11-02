package com.pathfinder.hub.ui.screens.books

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.repository.BookRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CreateBookUiState(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val externalUrl: String = "",
    val contentPath: String = "",
    val coverUrl: String = "",
    val isSaving: Boolean = false,
    val isUploading: Boolean = false,
    val uploaded: Boolean = false,
    val uploadProgress: Float = 0f,
    val saved: Boolean = false,
    val error: String? = null,
    val canCreate: Boolean = false,
)

@HiltViewModel
class CreateBookViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateBookUiState())
    val state: StateFlow<CreateBookUiState> = _state.asStateFlow()

    private var clubId: String? = null

    init {
        val userId = sessionManager.getUserId()
        if (userId != null) {
            viewModelScope.launch {
                val user = userRepository.getUser(userId)
                clubId = user?.clubId
                val allowed = user != null && (
                    user.role == "director" ||
                    user.role == "conference_admin" ||
                    user.directorPermissions.contains("content.edit")
                    )
                _state.update { it.copy(canCreate = allowed) }
            }
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v, error = null) }
    fun onAuthorChange(v: String) = _state.update { it.copy(author = v, error = null) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onExternalUrlChange(v: String) = _state.update { it.copy(externalUrl = v) }
    fun onContentPathChange(v: String) = _state.update { it.copy(contentPath = v) }
    fun onCoverUrlChange(v: String) = _state.update { it.copy(coverUrl = v) }

    /**
     * Загрузить файл книги (.txt/.fb2/.pdf) с устройства:
     *   - парсит файл через BookFileParser
     *   - uploadBookText -> Yandex (clubs/<clubId>/books/<id>.txt)
     *   - создаёт BookEntity с contentUrl + contentPath (локальная копия)
     *   - пушит в Firestore через SyncQueue
     */
    fun uploadFile(uri: Uri, context: Context) {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(error = "Введите название") }; return
        }
        if (s.author.isBlank()) {
            _state.update { it.copy(error = "Введите автора") }; return
        }
        val cid = clubId
        if (cid.isNullOrBlank()) {
            _state.update { it.copy(error = "Клуб не найден") }; return
        }
        val uid = sessionManager.getUserId()
        if (uid.isNullOrBlank()) {
            _state.update { it.copy(error = "Нет сессии") }; return
        }
        if (!s.canCreate) {
            _state.update { it.copy(error = "Нет прав на создание книги") }; return
        }

        _state.update { it.copy(isUploading = true, uploadProgress = 0.1f, error = null) }
        viewModelScope.launch {
            try {
                val book = bookRepository.saveBookFromFile(
                    uri = uri,
                    title = s.title.trim(),
                    author = s.author.trim(),
                    description = s.description.trim().ifBlank { null },
                    coverUrl = s.coverUrl.trim().ifBlank { null },
                    clubId = cid,
                    uploadedBy = uid,
                )
                val ok = !book.contentUrl.isNullOrBlank()
                _state.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = if (ok) 1f else 0.5f,
                        uploaded = true,       // книга создана — но окно НЕ закрываем
                        saved = false,         // не триггерим навигацию
                        error = if (ok) null else "Книга сохранена локально, но не загружена в облако",
                    )
                }
            } catch (e: Exception) {
                Log.e("CreateBookVM", "uploadFile failed", e)
                _state.update {
                    it.copy(isUploading = false, error = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(error = "Введите название") }
            return
        }
        if (s.author.isBlank()) {
            _state.update { it.copy(error = "Введите автора") }
            return
        }
        if (s.externalUrl.isBlank() && s.contentPath.isBlank()) {
            _state.update { it.copy(error = "Укажите ссылку или путь к тексту") }
            return
        }
        if (!s.canCreate) {
            _state.update { it.copy(error = "Нет прав на создание книги") }
            return
        }

        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val book = BookEntity(
                    id = "book_" + UUID.randomUUID().toString(),
                    title = s.title.trim(),
                    author = s.author.trim(),
                    level = 1,
                    description = s.description.trim().ifBlank { null },
                    externalUrl = s.externalUrl.trim().ifBlank { null },
                    contentPath = s.contentPath.trim().ifBlank { null },
                    coverUrl = s.coverUrl.trim().ifBlank { null },
                    clubId = clubId,
                    pendingSync = clubId != null,
                )
                bookRepository.upsert(book)
                if (clubId != null) {
                    try { bookRepository.pushClubBook(book) } catch (_: Exception) {}
                }
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.message ?: "Ошибка сохранения") }
            }
        }
    }
}
