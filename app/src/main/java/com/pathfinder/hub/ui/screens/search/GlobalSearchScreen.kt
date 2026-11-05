package com.pathfinder.hub.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit = {},
    onOpenEvent: (eventId: String) -> Unit = {},
    onOpenHonor: (honorId: String) -> Unit = {},
    onOpenBook: (bookId: String) -> Unit = {},
    onOpenTask: (taskId: String) -> Unit = {},
    onOpenInfo: (articleId: String) -> Unit = {},
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Что искать: люди, события, специализации") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                state.isSearching -> Box(
                    Modifier.fillMaxSize(),
                    Alignment.Center
                ) { CircularProgressIndicator() }

                state.query.isBlank() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Search, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Введите запрос для поиска",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                state.results.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    Alignment.Center
                ) {
                    Text(
                        "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.results, key = { "${it::class.simpleName}:${it.id}" }) { r ->
                        when (r) {
                            is SearchResult.User -> ResultCard(
                                icon = Icons.Default.Person,
                                title = r.title,
                                subtitle = r.subtitle,
                                onClick = { onOpenUser(r.id) }
                            )
                            is SearchResult.Event -> ResultCard(
                                icon = Icons.Default.Event,
                                title = r.title,
                                subtitle = r.subtitle,
                                onClick = { onOpenEvent(r.id) }
                            )
                            is SearchResult.Honor -> ResultCard(
                                icon = Icons.Default.WorkspacePremium,
                                title = r.title,
                                subtitle = r.subtitle,
                                onClick = { onOpenHonor(r.id) }
                            )
                            is SearchResult.Book -> ResultCard(
                                icon = Icons.Default.MenuBook,
                                title = r.title,
                                subtitle = r.subtitle,
                                onClick = { onOpenBook(r.id) }
                            )
                            is SearchResult.Task -> ResultCard(
                                icon = Icons.Default.Assignment,
                                title = r.title,
                                subtitle = r.subtitle,
                                onClick = { onOpenTask(r.id) }
                            )
                            is SearchResult.InfoArticle -> ResultCard(
                                icon = Icons.Default.Info,
                                title = r.title,
                                subtitle = r.subtitle,
                                onClick = { onOpenInfo(r.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = PathfinderBlue)
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
