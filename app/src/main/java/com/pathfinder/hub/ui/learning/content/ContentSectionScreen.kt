package com.pathfinder.hub.ui.learning.content

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSectionScreen(
    onBack: () -> Unit,
    onOpenRequirement: (levelId: String, requirementId: String) -> Unit,
    viewModel: ContentSectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.sectionName.ifBlank { "Раздел" }) },
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
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.errorMessage != null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
            state.requirements.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Требований нет", style = MaterialTheme.typography.bodyLarge)
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.requirements, key = { it.id }) { req ->
                        Card(
                            onClick = { onOpenRequirement(viewModel.levelId, req.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, null, tint = PathfinderBlue, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        req.text.take(120) + if (req.text.length > 120) "…" else "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "ID: ${req.id}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
