package com.pathfinder.hub.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.GlobalSearchRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchResult {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String

    data class User(
        override val id: String,
        override val title: String,
        override val subtitle: String,
    ) : SearchResult()

    data class Event(
        override val id: String,
        override val title: String,
        override val subtitle: String,
    ) : SearchResult()

    data class Honor(
        override val id: String,
        override val title: String,
        override val subtitle: String,
    ) : SearchResult()

    data class Book(
        override val id: String,
        override val title: String,
        override val subtitle: String,
    ) : SearchResult()

    data class Task(
        override val id: String,
        override val title: String,
        override val subtitle: String,
    ) : SearchResult()

    data class InfoArticle(
        override val id: String,
        override val title: String,
        override val subtitle: String,
    ) : SearchResult()
}

data class GlobalSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val role: String? = null,
    val errorMessage: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val searchRepository: GlobalSearchRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(GlobalSearchUiState())
    val state: StateFlow<GlobalSearchUiState> = _state.asStateFlow()

    private val _queryFlow = MutableStateFlow("")

    init {
        val uid = sessionManager.getUserId()
        if (uid != null) {
            viewModelScope.launch {
                val user = userRepository.getUser(uid)
                _state.update { it.copy(role = user?.role) }
            }
        }
        viewModelScope.launch {
            _queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { q -> runSearch(q) }
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        _queryFlow.value = q
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        _state.update { it.copy(isSearching = true) }
        try {
            val results = mutableListOf<SearchResult>()
            val q = query.trim()

            // users — только для staff-ролей
            val role = _state.value.role
            if (role in listOf("director", "instructor", "secretary")) {
                searchRepository.searchUsers(q).forEach { u ->
                    val name = "${u.firstName} ${u.lastName}".trim().ifBlank { u.email }
                    results.add(
                        SearchResult.User(
                            id = u.id,
                            title = name,
                            subtitle = "${u.role} · ${u.email}"
                        )
                    )
                }
            }

            searchRepository.searchEvents(q).forEach { e ->
                results.add(
                    SearchResult.Event(
                        id = e.id,
                        title = e.title,
                        subtitle = "Событие · ${e.dateStart}"
                    )
                )
            }

            searchRepository.searchHonors(q).forEach { h ->
                results.add(
                    SearchResult.Honor(
                        id = h.id,
                        title = h.name,
                        subtitle = "Специализация · Уровень ${h.level}"
                    )
                )
            }

            searchRepository.searchBooks(q).forEach { b ->
                results.add(
                    SearchResult.Book(
                        id = b.id,
                        title = b.title,
                        subtitle = "Книга · ${b.author.ifBlank { "Автор не указан" }}"
                    )
                )
            }

            searchRepository.searchTasks(q).forEach { t ->
                results.add(
                    SearchResult.Task(
                        id = t.id,
                        title = t.title,
                        subtitle = "Задание · ${t.status}"
                    )
                )
            }

            searchRepository.searchInfoArticles(q).forEach { a ->
                results.add(
                    SearchResult.InfoArticle(
                        id = a.id,
                        title = a.title,
                        subtitle = "Статья · ${a.categoryId}"
                    )
                )
            }

            _state.update { it.copy(results = results, isSearching = false) }
        } catch (e: Exception) {
            _state.update { it.copy(isSearching = false, errorMessage = e.message) }
        }
    }
}
