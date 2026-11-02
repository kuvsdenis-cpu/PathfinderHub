package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.BookReportEntity
import com.pathfinder.hub.data.repository.BookReportRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaffBooksUiState(
    val isLoading: Boolean = true,
    val pending: List<BookReportEntity> = emptyList(),
    val reviewed: List<BookReportEntity> = emptyList(),
    val childNames: Map<String, String> = emptyMap(),
    val selectedTab: Int = 0,
    val errorMessage: String? = null,
    val message: String? = null,
)

@HiltViewModel
class StaffBooksViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val bookReportRepository: BookReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StaffBooksUiState())
    val state: StateFlow<StaffBooksUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                bookReportRepository.observePending(),
                bookReportRepository.observeReviewed(),
            ) { pending, reviewed ->
                val names = mutableMapOf<String, String>()
                (pending + reviewed).map { it.userId }.distinct().forEach { uid ->
                    val u = userRepository.getUser(uid)
                    if (u != null) names[uid] = "${u.firstName} ${u.lastName}".trim()
                }
                Triple(pending, reviewed, names)
            }.collect { (pending, reviewed, names) ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        pending = pending,
                        reviewed = reviewed,
                        childNames = names,
                    )
                }
            }
        }
    }

    fun setTab(idx: Int) = _state.update { it.copy(selectedTab = idx) }

    fun approve(r: BookReportEntity) {
        val uid = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                bookReportRepository.approve(r, uid)
                _state.update { it.copy(message = "Одобрено") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun reject(r: BookReportEntity) {
        val uid = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                bookReportRepository.reject(r, uid)
                _state.update { it.copy(message = "Возвращено") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, message = null) }
}
