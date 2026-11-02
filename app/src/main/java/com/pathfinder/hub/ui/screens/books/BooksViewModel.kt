package com.pathfinder.hub.ui.screens.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.repository.BookRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.data.local.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BooksUiState(
    val isLoading: Boolean = true,
    val books: List<BookEntity> = emptyList(),
    val clubId: String? = null,
    val canCreateBook: Boolean = false,
    val canEditBook: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val userRepository: UserRepository,
    sessionManager: SessionManager
) : ViewModel() {

    private val userId: String? = sessionManager.getUserId()

    private val clubIdFlow: StateFlow<String?> =
        if (userId == null) MutableStateFlow<String?>(null)
        else userRepository.observeUser(userId)
            .map { it?.clubId }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val canCreateFlow: StateFlow<Boolean> =
        if (userId == null) MutableStateFlow(false)
        else userRepository.observeUser(userId)
            .map { user ->
                user != null && (
                    user.role == "director" ||
                    user.role == "conference_admin" ||
                    user.directorPermissions.contains("content.edit")
                )
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val canEditFlow: StateFlow<Boolean> =
        if (userId == null) MutableStateFlow(false)
        else userRepository.observeUser(userId)
            .map { user ->
                user != null && (
                    user.role == "conference_admin" ||
                    user.isOwnerClub ||
                    user.directorPermissions.contains("content.edit")
                )
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val booksFlow = clubIdFlow.flatMapLatest { clubId ->
        bookRepository.observeAvailable(clubId)
    }

    val state: StateFlow<BooksUiState> =
        combine(booksFlow, clubIdFlow, canCreateFlow, canEditFlow) { books, clubId, canCreate, canEdit ->
            BooksUiState(
                isLoading = false,
                books = books,
                clubId = clubId,
                canCreateBook = canCreate,
                canEditBook = canEdit,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BooksUiState())

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            try {
                bookRepository.deleteClubBook(book)
            } catch (_: Exception) { }
        }
    }

    fun deleteBrokenBooks() {
        viewModelScope.launch { try { bookRepository.deleteBrokenBooks() } catch (_: Exception) {} }
    }
    fun updateBook(book: BookEntity, title: String, author: String, description: String?, coverUrl: String?) {
        viewModelScope.launch {
            try {
                val updated = book.copy(
                    title = title.trim(),
                    author = author.trim(),
                    description = description?.trim()?.ifBlank { null },
                    coverUrl = coverUrl?.trim()?.ifBlank { null },
                    pendingSync = book.clubId != null,
                )
                bookRepository.upsert(updated)
                if (book.clubId != null) {
                    bookRepository.pushClubBook(updated)
                }
            } catch (_: Exception) { }
        }
    }
}
