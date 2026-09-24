package com.pathfinder.hub.ui.screens.honors

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGray

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HonorCatalogScreen(
    onBack: () -> Unit,
    onOpenHonor: (String) -> Unit,
    viewModel: HonorCatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Специализации") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
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
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("Поиск") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.levelFilter == null,
                        onClick = { viewModel.onLevelFilterChange(null) },
                        label = { Text("Все уровни") }
                    )
                }
                items(listOf(1, 2, 3)) { level ->
                    FilterChip(
                        selected = state.levelFilter == level,
                        onClick = { viewModel.onLevelFilterChange(level) },
                        label = { Text("Уровень $level") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AssistChip(
                        onClick = { viewModel.onCategorySelected(null) },
                        label = { Text("Все") },
                        colors = if (state.selectedCategoryId == null)
                            AssistChipDefaults.assistChipColors(
                                containerColor = PathfinderBlue.copy(alpha = 0.2f)
                            )
                        else AssistChipDefaults.assistChipColors()
                    )
                }
                items(state.categories, key = { it.id }) { cat ->
                    AssistChip(
                        onClick = { viewModel.onCategorySelected(cat.id) },
                        label = { Text(cat.name) },
                        colors = if (state.selectedCategoryId == cat.id)
                            AssistChipDefaults.assistChipColors(
                                containerColor = PathfinderBlue.copy(alpha = 0.2f)
                            )
                        else AssistChipDefaults.assistChipColors()
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Найдено: ${state.honors.size}",
                style = MaterialTheme.typography.labelLarge,
                color = PathfinderGray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.honors, key = { it.id }) { honor ->
                    HonorCard(honor, onClick = { onOpenHonor(honor.id) })
                }
            }
        }
    }
}

@Composable
private fun HonorCard(honor: HonorEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = honor.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Уровень ${honor.level}",
                        style = MaterialTheme.typography.labelLarge,
                        color = PathfinderGray
                    )
                }
            }
            if (honor.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = honor.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PathfinderGray,
                    maxLines = 2
                )
            }
        }
    }
}