package com.pathfinder.hub.ui.qr

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageStationsScreen(
    onBack: () -> Unit,
    onCreateStation: () -> Unit,
    onOpenQuestReadonly: (questId: String) -> Unit = {},
    viewModel: ManageStationsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR-станции") },
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
        ManageStationsContent(
            onCreateStation = onCreateStation,
            onOpenQuestReadonly = onOpenQuestReadonly,
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
        )
    }
}

@Composable
fun ManageStationsContent(
    onCreateStation: () -> Unit,
    onOpenQuestReadonly: (questId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ManageStationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = state.activeTab) {
                    Tab(
                        selected = state.activeTab == 0,
                        onClick = { viewModel.setTab(0) },
                        text = { Text("Одиночные (${state.standalone.size})") }
                    )
                    Tab(
                        selected = state.activeTab == 1,
                        onClick = { viewModel.setTab(1) },
                        text = { Text("Квесты (${state.inQuests.size})") }
                    )
                }
                if (state.activeTab == 0) {
                    if (state.standalone.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                            Text(
                                "Одиночных станций пока нет.\nСоздайте через +",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.standalone, key = { it.id }) { s ->
                                StationCard(
                                    s = s,
                                    onShowQr = {
                                        scope.launch {
                                            val f = withContext(Dispatchers.IO) {
                                                StationQrHelper.generatePng(ctx, s.id)
                                            }
                                            StationQrHelper.open(ctx, f)
                                        }
                                    },
                                    onShareQr = {
                                        scope.launch {
                                            val f = withContext(Dispatchers.IO) {
                                                StationQrHelper.generatePng(ctx, s.id)
                                            }
                                            StationQrHelper.share(ctx, s.name, f)
                                        }
                                    },
                                    onCopyPayload = {
                                        Toast.makeText(
                                            ctx,
                                            StationQrHelper.payload(s.id),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onDelete = { viewModel.deleteStation(s.id) },
                                    canArchive = state.canArchive,
                                    onArchive = { viewModel.archiveStation(s.id) },
                                )
                            }
                        }
                    }
                } else {
                    if (state.questsWithStations.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                            Text(
                                "Станций в квестах пока нет.\nДобавьте в редакторе квеста.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.questsWithStations.forEach { qws ->
                                item(key = "quest_${qws.quest.id}") {
                                    Card(
                                        onClick = { onOpenQuestReadonly(qws.quest.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    ) {
                                        Row(
                                            Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Star, null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    qws.quest.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Text(
                                                    "${qws.stations.size} станций",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                                items(qws.stations, key = { s -> "st_${s.id}" }) { s ->
                                    StationCard(
                                        s = s,
                                        onShowQr = {
                                            scope.launch {
                                                val f = withContext(Dispatchers.IO) {
                                                    StationQrHelper.generatePng(ctx, s.id)
                                                }
                                                StationQrHelper.open(ctx, f)
                                            }
                                        },
                                        onShareQr = {
                                            scope.launch {
                                                val f = withContext(Dispatchers.IO) {
                                                    StationQrHelper.generatePng(ctx, s.id)
                                                }
                                                StationQrHelper.share(ctx, s.name, f)
                                            }
                                        },
                                        onCopyPayload = {
                                            Toast.makeText(
                                                ctx,
                                                StationQrHelper.payload(s.id),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        },
                                        onDelete = { viewModel.deleteStation(s.id) },
                                        canArchive = false,
                                        onArchive = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateStation,
            containerColor = PathfinderBlue,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, "Создать станцию")
        }
    }
}

@Composable
private fun StationCard(
    s: QrStationEntity,
    onShowQr: () -> Unit,
    onShareQr: () -> Unit,
    onCopyPayload: () -> Unit,
    onDelete: () -> Unit,
    canArchive: Boolean = false,
    onArchive: () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Тип: " + s.type + " · ID: " + s.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (s.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(s.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Радиус: " + s.radiusMeters + " м · Очки: " + s.rewardPoints,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Координаты: " + s.latitude + ", " + s.longitude,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onShowQr) {
                    Icon(Icons.Default.QrCode, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("QR")
                }
                IconButton(onClick = onShareQr) {
                    Icon(Icons.Default.Share, "Поделиться", tint = PathfinderBlue)
                }
                IconButton(onClick = onCopyPayload) {
                    Icon(Icons.Default.QrCode, "Скопировать payload", tint = PathfinderBlue)
                }
                Spacer(Modifier.weight(1f))
                if (canArchive) {
                    IconButton(onClick = onArchive) {
                        Icon(Icons.Default.Archive, "В архив", tint = PathfinderBlue)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
