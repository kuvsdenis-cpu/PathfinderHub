package com.pathfinder.hub.ui.screens.levels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderRed

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequirementDetailScreen(
    onBack: () -> Unit,
    viewModel: RequirementDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Показываем сообщения
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Требование") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            state.requirement == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Требование не найдено", color = PathfinderRed) }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    // Хлебные крошки
                    Text(
                        text = "${state.levelName} • ${state.sectionName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = PathfinderBlue
                    )
                    Spacer(Modifier.height(12.dp))

                    // Текст требования
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = state.requirement!!.text,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Статус
                    StatusCard(state.status, state.comment)

                    Spacer(Modifier.weight(1f))

                    // Кнопки
                    if (state.status == "not_started" || state.status == "rejected") {
                        Button(
                            onClick = { showConfirmDialog = true },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Отметить как выполненное")
                            }
                        }
                    } else if (state.status == "submitted") {
                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text("Отменить сдачу")
                        }
                    }
                }
            }
        }
    }

    // Диалог подтверждения
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Подтвердить выполнение?") },
            text = { Text("Требование будет отправлено наставнику на проверку.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    viewModel.markAsCompleted()
                }) { Text("Да, отправить") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Диалог отмены
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Отменить сдачу?") },
            text = { Text("Требование вернётся в статус «не начато».") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.resetStatus()
                }) { Text("Да, отменить") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Назад") }
            }
        )
    }
}

@Composable
private fun StatusCard(status: String, comment: String?) {
    val (label, color) = when (status) {
        "not_started" -> "Не начато" to PathfinderBlue
        "in_progress" -> "В процессе" to PathfinderBlue
        "submitted" -> "На проверке" to PathfinderBlue
        "approved" -> "Сдано" to PathfinderGreen
        "rejected" -> "Отклонено" to PathfinderRed
        else -> status to PathfinderBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Статус",
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            if (!comment.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Комментарий: $comment",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PathfinderRed
                )
            }
        }
    }
}