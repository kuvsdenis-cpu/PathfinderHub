package com.pathfinder.hub.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
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
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderRed
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHistoryScreen(
    onNavigateBack: () -> Unit = {},
    onReportClick: (String) -> Unit = {},
    onCreateReport: () -> Unit = {},
    viewModel: ReportHistoryViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val clubState by clubContextViewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История отчётов") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            ReportHistoryContent(
                onReportClick = onReportClick,
                onCreateReport = onCreateReport,
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                clubContextViewModel = clubContextViewModel,
            )
        }
    }
}

@Composable
fun ReportHistoryContent(
    onReportClick: (String) -> Unit = {},
    onCreateReport: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReportHistoryViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clubState by clubContextViewModel.state.collectAsState()
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru"))

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else if (state.reports.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Отчёты ещё не генерировались", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Нажмите + чтобы создать первый отчёт", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.reports, key = { it.id }) { report ->
                        ReportCard(
                            report = report,
                            dateFormat = dateFormat,
                            canArchive = state.canArchive,
                            onClick = { onReportClick(report.id) },
                            onArchive = { viewModel.archiveReport(report.id) },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateReport,
            containerColor = PathfinderBlue,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, "Создать отчёт")
        }
    }
}

@Composable
private fun ReportCard(
    report: com.pathfinder.hub.data.local.entity.reports.ReportEntity,
    dateFormat: SimpleDateFormat,
    canArchive: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
) {
    val statusText = when {
        report.errorMessage != null -> "Ошибка"
        report.signed -> "Подписан"
        report.fileUrl != null -> "Готов"
        else -> "Черновик"
    }
    val (icon, color) = when {
        report.errorMessage != null -> Icons.Default.Error to PathfinderRed
        report.signed -> Icons.Default.CheckCircle to PathfinderGreen
        else -> Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (report.signed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Отчёт: ${report.level}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(text = "Период: ${dateFormat.format(report.periodStart)} - ${dateFormat.format(report.periodEnd)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Создан: ${dateFormat.format(report.generatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = statusText, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            if (canArchive) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onArchive) {
                    Icon(Icons.Default.Archive, "В архив", tint = PathfinderBlue, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
