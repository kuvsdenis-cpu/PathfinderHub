package com.pathfinder.hub.ui.info

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@HiltViewModel
class InfoHubViewModel @Inject constructor(
    repository: KnowledgeBaseRepository,
    sessionManager: SessionManager,
    userRepository: UserRepository,
) : ViewModel() {
    val categories: StateFlow<List<InfoCategory>> = repository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
fun InfoHubScreen(
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenEditor: (String?) -> Unit,
    viewModel: InfoHubViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Доп. информация") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    val canEdit by viewModel.canEdit.collectAsState()
                    if (canEdit) {
                        IconButton(onClick = { onOpenEditor(null) }) {
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryCard(category = category, onClick = { onOpenCategory(category.id) })
            }
        }
    }
}

@Composable
private fun CategoryCard(category: InfoCategory, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = category.icon, fontSize = 42.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
