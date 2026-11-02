package com.pathfinder.hub.ui.screens.books

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.local.entity.learning.BookReportEntity
import com.pathfinder.hub.data.repository.BookReportRepository
import com.pathfinder.hub.data.repository.BookRepository
import com.pathfinder.hub.data.repository.TestRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class BookDetailUiState(
    val isLoading: Boolean = true,
    val book: BookEntity? = null,
    val showReportDialog: Boolean = false,
    val canCreateTest: Boolean = false,
    val existingTestId: String? = null,
    val error: String? = null,
    val success: String? = null,
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookReportRepository: BookReportRepository,
    private val testRepository: TestRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""
    private val userId: String? = sessionManager.getUserId()

    private val _state = MutableStateFlow(BookDetailUiState())
    val state: StateFlow<BookDetailUiState> = _state.asStateFlow()

    val showReportDialog: Boolean get() = _state.value.showReportDialog
    val canCreateTest: Boolean get() = _state.value.canCreateTest
    val existingTestId: String? get() = _state.value.existingTestId

    init {
        viewModelScope.launch {
            bookRepository.observeById(bookId).collect { book ->
                _state.update { it.copy(isLoading = false, book = book) }
            }
        }
        loadPermissions()
        loadExistingTest()
    }

    private fun loadExistingTest() {
        val uid = userId ?: return
        viewModelScope.launch {
            try {
                val user = userRepository.getUser(uid) ?: return@launch
                val clubId = user.clubId ?: return@launch
                val tests = testRepository.getTestsForScope(clubId, "book", bookId)
                _state.update { it.copy(existingTestId = tests.firstOrNull()?.id) }
            } catch (_: Exception) {
                // нет метода / нет теста — оставляем null
            }
        }
    }

    private fun loadPermissions() {
        val uid = userId ?: return
        viewModelScope.launch {
            val user = userRepository.getUser(uid) ?: return@launch
            val allowed = user.role == "director"
                || user.role == "conference_admin"
                || user.role == "instructor"
                || PermissionChecker.hasPerm(user, "content.edit")
            _state.update { it.copy(canCreateTest = allowed) }
        }
    }

    fun openReportDialog() {
        _state.update { it.copy(showReportDialog = true) }
    }

    fun closeReportDialog() {
        _state.update { it.copy(showReportDialog = false) }
    }

    fun saveReport(content: String) {
        val uid = userId ?: return
        if (content.isBlank()) {
            _state.update { it.copy(error = "Введите текст отчёта") }
            return
        }
        viewModelScope.launch {
            try {
                val report = BookReportEntity(
                    id = UUID.randomUUID().toString(),
                    userId = uid,
                    bookId = bookId,
                    type = "summary",
                    content = content.trim(),
                    status = "pending",
                    approvedBy = null,
                    approvedAt = null,
                    pendingSync = true,
                )
                bookReportRepository.upsert(report)
                _state.update {
                    it.copy(showReportDialog = false, success = "Отчёт отправлен")
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка отправки") }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, success = null) }
    }
}
