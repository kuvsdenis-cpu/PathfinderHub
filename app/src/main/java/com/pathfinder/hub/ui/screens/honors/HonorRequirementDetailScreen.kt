package com.pathfinder.hub.ui.screens.honors

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import com.pathfinder.hub.ui.common.AttachmentPreview
import com.pathfinder.hub.ui.storage.FileUploadContext
import com.pathfinder.hub.ui.screens.books.RequirementBooksSection
import com.pathfinder.hub.ui.storage.FileUploadResult
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HonorRequirementDetailScreen(
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenBook: (bookId: String) -> Unit = {},
    navBackStackEntry: NavBackStackEntry? = null,
    viewModel: HonorRequirementDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(navBackStackEntry) {
        val handle = navBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        val flow = handle.getStateFlow<FileUploadResult?>(FileUploadResult.SAVED_STATE_KEY, null)
        flow.collect { result ->
            if (result != null && result.context == FileUploadContext.HONOR_REQUIREMENT) {
                viewModel.attachUploadedFile(result.fileUrl, result.fileName)
                handle.remove<FileUploadResult>(FileUploadResult.SAVED_STATE_KEY)
            }
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Требование") },
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
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val req = state.requirement ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(state.honorName, style = MaterialTheme.typography.labelLarge, color = PathfinderBlue)
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        req.text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Тип: ${req.type}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Статус: ${statusLabel(state.status)}",
                style = MaterialTheme.typography.bodyLarge,
                color = PathfinderBlue
            )

            // ✅ Блок прикреплённых файлов
            if (state.mediaUrls.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Прикреплённые файлы:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                state.mediaUrls.forEach { url ->
                    AttachmentPreview(url = url)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.books.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                RequirementBooksSection(
                    books = state.books,
                    onOpenBook = onOpenBook
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onOpenReport,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (state.mediaUrls.isEmpty()) "Загрузить отчёт" else "Загрузить ещё файл")
            }
            Spacer(Modifier.height(8.dp))
            if (state.status == "not_started" || state.status == "rejected") {
                OutlinedButton(
                    onClick = { viewModel.submitTheory() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Отметить как выполненное")
                }
            }
        }
    }
}

private fun statusLabel(s: String) = when (s) {
    "not_started" -> "Не начато"
    "submitted" -> "На проверке"
    "approved" -> "Сдано"
    "rejected" -> "Отклонено"
    else -> s
}