package com.pathfinder.hub.ui.screens.archive

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.screens.home.director.ClubContextViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    onBack: () -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val clubState by clubContextViewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Архив") },
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            ArchiveContent(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                clubContextViewModel = clubContextViewModel,
            )
        }
    }
}

@Composable
fun ArchiveContent(
    modifier: Modifier = Modifier,
    viewModel: ArchiveViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clubState by clubContextViewModel.state.collectAsState()
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))

    if (state.isLoading) {
        Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = state.activeTab, edgePadding = 8.dp) {
            Tab(selected = state.activeTab == 0, onClick = { viewModel.setTab(0) }, text = { Text("События (${state.archivedEvents.size})") })
            Tab(selected = state.activeTab == 1, onClick = { viewModel.setTab(1) }, text = { Text("Задания (${state.archivedTasks.size})") })
            Tab(selected = state.activeTab == 2, onClick = { viewModel.setTab(2) }, text = { Text("Домашки (${state.archivedHomework.size})") })
            Tab(selected = state.activeTab == 3, onClick = { viewModel.setTab(3) }, text = { Text("Чат (${state.archivedComments.size})") })
            Tab(selected = state.activeTab == 4, onClick = { viewModel.setTab(4) }, text = { Text("Квесты (${state.archivedQuests.size})") })
            Tab(selected = state.activeTab == 5, onClick = { viewModel.setTab(5) }, text = { Text("Станции (${state.archivedStations.size})") })
            Tab(selected = state.activeTab == 6, onClick = { viewModel.setTab(6) }, text = { Text("Отчёты (${state.archivedReports.size})") })
            Tab(selected = state.activeTab == 7, onClick = { viewModel.setTab(7) }, text = { Text("Группы (${state.archivedGroups.size})") })
        }

        when (state.activeTab) {
            0 -> if (state.archivedEvents.isEmpty()) EmptyBox("Архив событий пуст") else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { items(state.archivedEvents, key = { it.id }) { e -> ArchiveCard(e.title, e.location, e.archivedAt?.let { fmt.format(it) }) { viewModel.restoreEvent(e.id) } } }
            1 -> if (state.archivedTasks.isEmpty()) EmptyBox("Архив заданий пуст") else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { items(state.archivedTasks, key = { it.id }) { t -> ArchiveCard(t.title, t.description, t.archivedAt?.let { fmt.format(it) }) { viewModel.restoreTask(t.id) } } }
            2 -> if (state.archivedHomework.isEmpty()) EmptyBox("Архив домашек пуст") else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { items(state.archivedHomework, key = { it.id }) { h -> ArchiveCard(h.title, h.description, h.archivedAt?.let { fmt.format(it) }) { viewModel.restoreHomework(h.id) } } }
            3 -> if (state.archivedComments.isEmpty()) EmptyBox("Архив чата пуст") else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { items(state.archivedComments, key = { it.id }) { c -> ArchiveCard(c.text.take(60), "Автор: ${c.authorId.take(8)}… · ${c.targetType}", c.archivedAt?.let { fmt.format(it) }) { viewModel.restoreComment(c.id) } } }
            4 -> if (state.archivedQuests.isEmpty()) EmptyBox("Архив QR-квестов пуст") else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { items(state.archivedQuests, key = { it.id }) { q -> ArchiveCard(q.title, q.description, q.archivedAt?.let { fmt.format(it) }) { viewModel.restoreQuest(q.id) } } }
            5 -> if (state.archivedStations.isEmpty()) EmptyBox("Архив QR-станций пуст") else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { items(state.archivedStations, key = { it.id }) { s -> ArchiveCard(s.name, s.description, s.archivedAt?.let { fmt.format(it) }) { viewModel.restoreStation(s.id) } } }
            6 -> if (state.archivedReports.isEmpty()) EmptyBox("Архив отчётов пуст") else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { items(state.archivedReports, key = { it.id }) { r -> ArchiveCard("Отчёт: ${r.level}", r.fileUrl ?: "PDF не сгенерирован", r.archivedAt?.let { fmt.format(it) }) { viewModel.restoreReport(r.id) } } }
            7 -> if (state.archivedGroups.isEmpty()) EmptyBox("Архив групп пуст") else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { items(state.archivedGroups, key = { it.id }) { g -> ArchiveCard(g.name, g.description, g.archivedAt?.let { fmt.format(it) }) { viewModel.restoreGroup(g.id) } } }
        }
    }
}

@Composable
private fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Text("$text\nУдаление через 90 дней.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArchiveCard(title: String, subtitle: String, archivedAt: String?, onRestore: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (archivedAt != null) {
                Spacer(Modifier.height(4.dp))
                Text("В архиве с: $archivedAt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Восстановить")
            }
        }
    }
}
