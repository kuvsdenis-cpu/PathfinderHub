package com.pathfinder.hub.ui.info

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.KnowledgeBaseRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.model.InfoCategory
import com.pathfinder.hub.ui.theme.PathfinderBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InfoCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: KnowledgeBaseRepository,
    sessionManager: SessionManager,
    userRepository: UserRepository,
) : ViewModel() {
    val categoryId: String = savedStateHandle.get<String>("categoryId") ?: ""
    val category: StateFlow<InfoCategory?> = repository.getCategories()
        .map { list -> list.find { it.id == categoryId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val canEdit: StateFlow<Boolean> = flow {
        val uid = sessionManager.getUserId()
        val user = if (uid.isNullOrBlank()) null else userRepository.getUser(uid)
        val role = user?.role ?: ""
        emit(
            role in listOf("director", "instructor", "conference_admin") ||
                (role == "director" && (user?.directorPermissions?.contains("content.edit") == true))
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoCategoryScreen(
    onBack: () -> Unit,
    onOpenArticle: (String, String) -> Unit,
    onOpenEditor: (String) -> Unit,
    viewModel: InfoCategoryViewModel = hiltViewModel()
) {
    val category by viewModel.category.collectAsState()
    val categoryId = viewModel.categoryId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.title ?: "Информация") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    val canEdit by viewModel.canEdit.collectAsState()
                    if (canEdit) {
                        IconButton(onClick = { onOpenEditor(categoryId) }) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить статью")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        val cat = category ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cat.articles, key = { it.id }) { article ->
                Card(
                    onClick = { onOpenArticle(categoryId, article.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = article.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}
