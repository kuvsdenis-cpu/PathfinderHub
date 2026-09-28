package com.pathfinder.hub.ui.reports

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.reports.ReportEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    reportId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: ReportDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(reportId) {
        viewModel.loadReport(reportId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали отчёта") },
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
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val report = state.report
        if (report == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Отчёт не найден", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Карточка с основной информацией
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = PathfinderBlue, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Отчёт: ${report.level}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))

                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
                    InfoRow("Период", "${dateFormat.format(report.periodStart)} – ${dateFormat.format(report.periodEnd)}")
                    InfoRow("Статус", getStatusText(report.status, report.signed))
                    InfoRow("Дата создания", dateFormat.format(report.generatedAt))
                    InfoRow("Автор", report.generatedBy)

                    if (report.errorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Ошибка: ${report.errorMessage}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Кнопки действий
            if (report.fileUrl != null) {
                Button(
                    onClick = { openOrSharePdf(context, report.fileUrl, report.level) },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть / Поделиться PDF")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun getStatusText(status: String, signed: Boolean): String {
    return when {
        signed -> "Подписан"
        status == "draft" -> "Черновик"
        else -> status.replaceFirstChar { it.uppercase() }
    }
}

private fun openOrSharePdf(context: Context, filePath: String, reportName: String) {
    val file = File(filePath)
    if (!file.exists()) {
        Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        // Используем FileProvider для безопасного доступа к файлу (Android 7.0+)
        // Убедитесь, что в AndroidManifest.xml настроен FileProvider с authority = "${applicationId}.fileprovider"
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        // Если нет приложения для просмотра PDF, предложим поделиться
        val chooser = Intent.createChooser(intent, "Открыть или поделиться отчётом")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось открыть PDF. Убедитесь, что настроен FileProvider.", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}