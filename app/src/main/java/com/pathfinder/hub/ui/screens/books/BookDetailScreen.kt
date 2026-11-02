package com.pathfinder.hub.ui.screens.books

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    onBack: () -> Unit,
    onRead: (bookId: String) -> Unit = {},
    onCreateTest: (bookId: String) -> Unit = {},
    onOpenTest: (testId: String) -> Unit = {},
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.book?.title ?: "Книга") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (viewModel.canCreateTest) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить книгу")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val book = state.book
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (book == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Книга не найдена", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(book.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(book.author, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!book.description.isNullOrBlank()) {
                Text(book.description, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            if (!book.externalUrl.isNullOrBlank()) {
                Button(
                    onClick = {
                        val url = book.externalUrl!!
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Открыть ссылку")
                }
            }
            if (!book.contentPath.isNullOrBlank()) {
                Button(
                    onClick = { onRead(book.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Читать")
                }
            }
            if (book.externalUrl.isNullOrBlank() && book.contentPath.isNullOrBlank()) {
                Text("Источник для этой книги пока не указан.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            Text("Отчёт по книге", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.openReportDialog() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Написать отчёт (пересказ)")
            }

            if (viewModel.canCreateTest) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onCreateTest(book.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Создать тест по книге")
                }
            }

            val testId = viewModel.existingTestId
            if (testId != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onOpenTest(testId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Пройти тест")
                }
            }

            // Диалог отчёта
            if (viewModel.showReportDialog) {
                var reportText by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { viewModel.closeReportDialog() },
                    title = { Text("Отчёт по книге") },
                    text = {
                        OutlinedTextField(
                            value = reportText,
                            onValueChange = { reportText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            label = { Text("Краткий пересказ") },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.saveReport(reportText)
                        }) { Text("Отправить") }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.closeReportDialog() }) { Text("Отмена") }
                    }
                )
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Удалить книгу?") },
                    text = { Text("Книга будет удалена. Это действие нельзя отменить.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteDialog = false
                            viewModel.deleteBook { onBack() }
                        }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
                    }
                )
            }
        }
    }
}
