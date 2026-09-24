package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.learning.HonorCategoryEntity
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.repository.HonorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HonorCatalogUiState(
    val isLoading: Boolean = true,
    val categories: List<HonorCategoryEntity> = emptyList(),
    val honors: List<HonorEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val levelFilter: Int? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HonorCatalogViewModel @Inject constructor(
    private val honorRepository: HonorRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HonorCatalogUiState())
    val state: StateFlow<HonorCatalogUiState> = _state.asStateFlow()

    private var allHonors: List<HonorEntity> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            honorRepository.observeCategories().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }

        viewModelScope.launch {
            honorRepository.observeAllHonors().collect { honors ->
                allHonors = honors
                applyFilters()
            }
        }
    }

    fun onCategorySelected(categoryId: String?) {
        _state.update { it.copy(selectedCategoryId = categoryId) }
        applyFilters()
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onLevelFilterChange(level: Int?) {
        _state.update { it.copy(levelFilter = level) }
        applyFilters()
    }

    private fun applyFilters() {
        val s = _state.value
        val filtered = allHonors.filter { honor ->
            val matchesCategory = s.selectedCategoryId == null ||
                    honor.categoryId == s.selectedCategoryId
            val matchesLevel = s.levelFilter == null || honor.level == s.levelFilter
            val matchesSearch = s.searchQuery.isBlank() ||
                    honor.name.contains(s.searchQuery, ignoreCase = true)
            matchesCategory && matchesLevel && matchesSearch
        }.sortedBy { it.name }

        _state.update { it.copy(honors = filtered, isLoading = false) }
    }
}