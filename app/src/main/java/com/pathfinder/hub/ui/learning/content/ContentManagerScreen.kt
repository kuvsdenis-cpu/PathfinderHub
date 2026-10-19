package com.pathfinder.hub.ui.learning.content

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.screens.home.director.ClubContextViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentManagerScreen(
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
    onOpenHonor: (String) -> Unit,
    onOpenTest: (String) -> Unit,
    viewModel: ContentManagerViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val clubState by clubContextViewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление контентом") },
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
            ContentManagerContent(
                onOpenLevel = onOpenLevel,
                onOpenHonor = onOpenHonor,
                onOpenTest = onOpenTest,
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                clubContextViewModel = clubContextViewModel,
            )
        }
    }
}

@Composable
fun ContentManagerContent(
    onOpenLevel: (String) -> Unit = {},
    onOpenHonor: (String) -> Unit = {},
    onOpenTest: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ContentManagerViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clubState by clubContextViewModel.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Ступени") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Специализации") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Тесты") })
        }

        when (tab) {
            0 -> {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else if (state.errorMessage != null) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.levels, key = { it.level.id }) { item ->
                            LevelRow(item = item, onClick = { onOpenLevel(item.level.id) })
                        }
                    }
                }
            }
            1 -> {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else if (state.honors.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Специализаций пока нет", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.honors, key = { it.honor.id }) { item ->
                            HonorRow(item = item, onClick = { onOpenHonor(item.honor.id) })
                        }
                    }
                }
            }
            2 -> {
                TestsTab(
                    tests = state.tests,
                    canEdit = state.canEdit,
                    onCreateTest = { onOpenTest("new") },
                    onOpenTest = { id -> onOpenTest(id) },
                )
            }
        }
    }
}

@Composable
private fun TestsTab(
    tests: List<com.pathfinder.hub.data.local.entity.learning.TestEntity>,
    canEdit: Boolean,
    onCreateTest: () -> Unit,
    onOpenTest: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (tests.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (canEdit) "Тестов пока нет. Нажмите + чтобы создать."
                    else "Тестов пока нет.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tests, key = { it.id }) { t ->
                    Card(onClick = { onOpenTest(t.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${t.scope}${t.scopeId?.let { " · $it" } ?: ""} · порог ${t.passScore}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (canEdit) {
            FloatingActionButton(
                onClick = onCreateTest,
                containerColor = PathfinderBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Default.Add, "Создать тест")
            }
        }
    }
}

@Composable
private fun HonorRow(item: HonorWithCount, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WorkspacePremium, null, tint = PathfinderBlue, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.honor.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val sub = buildString {
                    if (item.categoryName.isNotBlank()) { append(item.categoryName); append(" · ") }
                    append("${item.requirementCount} требований")
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LevelRow(item: LevelWithCount, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.School, null, tint = PathfinderBlue, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.level.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.sectionCount} разделов · ${item.requirementCount} требований",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
