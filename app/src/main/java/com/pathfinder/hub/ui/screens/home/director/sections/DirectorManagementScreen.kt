package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.domain.permissions.TilePermissions
import com.pathfinder.hub.ui.reports.ReportHistoryContent
import com.pathfinder.hub.ui.screens.archive.ArchiveContent
import com.pathfinder.hub.ui.screens.home.director.ClubContextViewModel
import com.pathfinder.hub.ui.screens.home.director.ClubSettingsContent
import com.pathfinder.hub.ui.screens.home.director.deleted.DeletedClubsContent
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorManagementScreen(
    onBack: () -> Unit,
    onCreateReport: () -> Unit = {},
    onOpenReport: (String) -> Unit = {},
    onNavigateToSync: () -> Unit = {},
    onOpenAppeals: () -> Unit = {},
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val clubState by clubContextViewModel.state.collectAsState()
    val user = clubState.currentUser
    var tab by remember { mutableIntStateOf(0) }

    val tabs = buildList {
        if (TilePermissions.isVisible(user, TilePermissions.SETTINGS)) add("Настройки")
    if (TilePermissions.isVisible(user, TilePermissions.SETTINGS)) add("Аудит")
        if (TilePermissions.isVisible(user, TilePermissions.REPORTS)) add("Отчёты")
        if (TilePermissions.isVisible(user, TilePermissions.ARCHIVE)) add("Архив")
        if (TilePermissions.isVisible(user, TilePermissions.MODERATION)) add("Апелляции")
        if (clubState.currentUser?.isOwnerClub == true) add("Удалённые")
    }

    LaunchedEffect(tabs.size) {
        if (tab >= tabs.size) tab = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(tabs) { idx, title ->
                    SectionTile(
                        icon = iconFor(title),
                        title = title,
                        onClick = { tab = idx },
                        selected = tab == idx,
                    )
                }
            }

            Button(
                onClick = onNavigateToSync,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Синхронизация")
            }

            Column(Modifier.fillMaxSize()) {
                when (tabs.getOrNull(tab)) {
                    "Настройки" -> ClubSettingsContent(
                        onBack = onBack,
                    )
                    "Аудит" -> AuditContent()
                    "Отчёты" -> ReportHistoryContent(
                        onCreateReport = onCreateReport,
                        onReportClick = onOpenReport,
                    )
                    "Апелляции" -> {
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            onOpenAppeals()
                        }
                    }
                    "Архив" -> ArchiveContent()
                    "Удалённые" -> DeletedClubsContent()
                    else -> {}
                }
            }
        }
    }
}

private fun iconFor(title: String): ImageVector = when (title) {
    "О клубе" -> Icons.Default.Info
    "Настройки" -> Icons.Default.Settings
    "Аудит" -> Icons.Default.History
    "Отчёты" -> Icons.Default.Description
    "Апелляции" -> Icons.Default.Gavel
    "Архив" -> Icons.Default.Archive
    "Удалённые" -> Icons.Default.Delete
    else -> Icons.Default.Settings
}

@Composable
private fun SectionTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = PathfinderBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
