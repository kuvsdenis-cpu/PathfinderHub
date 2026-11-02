package com.pathfinder.hub.ui.screens.home.director.scout

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.screens.home.director.ClubContextViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutProgressScreen(
    onOpenScout: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScoutProgressViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clubState by clubContextViewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(clubState.selectedClubId) {
        viewModel.setClubId(clubState.selectedClubId)
    }

    val filtered = state.scouts
        .filter { s -> state.filterLevelId == null || s.currentLevelId == state.filterLevelId }
        .filter { s ->
            when (state.filterMode) {
                FilterMode.ALL -> true
                FilterMode.BEHIND -> s.levelPercent < 100
                FilterMode.READY -> s.levelPercent == 100
            }
        }
        .filter { s ->
            query.isBlank() ||
                (s.user.firstName + " " + s.user.lastName).contains(query, ignoreCase = true)
        }

    // Column(verticalScroll) — без LazyColumn-багов
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; viewModel.onSearchQuery(it) },
            label = { Text("Поиск по имени") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.filterMode == FilterMode.ALL,
                onClick = { viewModel.onFilterMode(FilterMode.ALL) },
                label = { Text("Все") },
            )
            FilterChip(
                selected = state.filterMode == FilterMode.BEHIND,
                onClick = { viewModel.onFilterMode(FilterMode.BEHIND) },
                label = { Text("Отстающие") },
            )
            FilterChip(
                selected = state.filterMode == FilterMode.READY,
                onClick = { viewModel.onFilterMode(FilterMode.READY) },
                label = { Text("Готовы") },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.filterLevelId == null,
                onClick = { viewModel.onFilterLevel(null) },
                label = { Text("Все ступени") },
            )
            state.allLevels.forEach { lvl ->
                FilterChip(
                    selected = state.filterLevelId == lvl.id,
                    onClick = { viewModel.onFilterLevel(lvl.id) },
                    label = { Text(lvl.name) },
                )
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                Text("Нет следопытов", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            filtered.forEach { s ->
                ScoutCard(s = s, onClick = { onOpenScout(s.user.id) })
            }
        }
    }
}

@Composable
private fun ScoutCard(s: ScoutSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = PathfinderBlue, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (s.user.firstName + " " + s.user.lastName).trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        s.currentLevelName ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${s.levelPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PathfinderBlue,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Ступень: " + s.levelRequirementsApproved + "/" + s.levelRequirementsTotal +
                    " · В работе: " + s.honorsInProgress +
                    " · Завершено: " + s.honorsCompleted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
