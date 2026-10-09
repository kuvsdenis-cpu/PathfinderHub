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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import coil.compose.AsyncImage
import com.pathfinder.hub.ui.learning.RequirementContentViewModel
import com.pathfinder.hub.ui.learning.RequirementContentSection
import androidx.compose.material.icons.filled.Edit
import com.pathfinder.hub.ui.common.AttachmentPreview
import com.pathfinder.hub.ui.storage.FileUploadResult
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderRed

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequirementDetailScreen(
    onBack: () -> Unit,
    onUploadReport: (String, String) -> Unit,
    onOpenEditor: (String, String) -> Unit = { _, _ -> },
    onAttachTaskFile: (requirementId: String, taskId: String) -> Unit = { _, _ -> },
    savedStateHandle: SavedStateHandle,
    viewModel: RequirementDetailViewModel = hiltViewModel(),
    contentViewModel: RequirementContentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var hasRequiredTasks by remember { mutableStateOf(false) }
    var allRequiredDone by remember { mutableStateOf(true) }

    val pendingAttachment by savedStateHandle
        .getStateFlow<FileUploadResult?>(FileUploadResult.SAVED_STATE_KEY, null)
        .collectAsState()

    LaunchedEffect(pendingAttachment) {
        pendingAttachment?.let { result ->
            if (result.context == com.pathfinder.hub.ui.storage.FileUploadContext.TASK_ANSWER) {
                // contextId = "requirementId|taskId"
                val taskId = result.contextId.substringAfter("|", "")
                if (taskId.isNotBlank()) {
                    contentViewModel.attachFileToTask(taskId, result.fileUrl)
                }
            } else {
                // LEVEL_REQUIREMENT, HONOR_REQUIREMENT и т.д. — старая логика
                viewModel.attachUploadedFile(result)
            }
            savedStateHandle.remove<FileUploadResult>(FileUploadResult.SAVED_STATE_KEY)
        }
    }

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
                actions = {
                    val canOpenEditor = state.userRole in listOf(
                        "director", "conference_admin", "instructor", "secretary"
                    )
                    if (canOpenEditor) {
                        IconButton(onClick = { showEditor = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                        }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.requirement == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Требование не найдено",
                            style = MaterialTheme.typography.titleLarge,
                            color = PathfinderRed
                        )
                        if (state.errorMessage != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                state.errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (state.levelName.isNotBlank() || state.sectionName.isNotBlank()) {
                        Text(
                            text = buildString {
                                if (state.levelName.isNotBlank()) append(state.levelName)
                                if (state.sectionName.isNotBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(state.sectionName)
                                }
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = PathfinderBlue
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // Полный текст требования
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

                    // ===== ИЛЛЮСТРАЦИИ ИЗ PDF =====
                    if (state.imageUrls.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Иллюстрации:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        state.imageUrls.forEach { pathOrUrl ->
                            val model: Any = if (
                                pathOrUrl.startsWith("http://") ||
                                pathOrUrl.startsWith("https://") ||
                                pathOrUrl.startsWith("file://")
                            ) {
                                pathOrUrl
                            } else {
                                "file:///android_asset/$pathOrUrl"
                            }
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    StatusCard(state.status, state.comment)

                    RequirementContentSection(
                        viewModel = contentViewModel,
                        onCompletionChanged = { hasReq, allDone ->
                            hasRequiredTasks = hasReq
                            allRequiredDone = allDone
                        },
                        onAttachFile = { taskId ->
                            onAttachTaskFile(state.requirement?.id ?: "", taskId)
                        }
                    )

                    // Прикреплённые пользователем файлы
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

                    Spacer(Modifier.height(24.dp))

                    val req = state.requirement
                    if (req != null) {
                        Button(
                            onClick = { onUploadReport(viewModel.getLevelId(), req.id) },
                            enabled = !state.isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (state.mediaUrls.isEmpty()) "Загрузить отчёт"
                                else "Загрузить ещё файл"
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    when (state.status) {
                        "not_started", "rejected" -> {
                            val canSubmit = !hasRequiredTasks || allRequiredDone
                            Button(
                                onClick = { showConfirmDialog = true },
                                enabled = !state.isSaving && canSubmit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        if (!hasRequiredTasks || allRequiredDone)
                                            "Отметить как выполненное"
                                        else
                                            "Сначала выполните все задания"
                                    )
                                }
                            }
                            if (hasRequiredTasks && !allRequiredDone) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Все обязательные задания должны быть проверены и приняты.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        "submitted" -> {
                            OutlinedButton(
                                onClick = { showResetDialog = true },
                                enabled = !state.isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = PathfinderBlue,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Отменить сдачу")
                                }
                            }
                        }

                        "approved" -> {
                            Text(
                                "Требование уже сдано и одобрено",
                                style = MaterialTheme.typography.bodyLarge,
                                color = PathfinderGreen,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        "in_progress" -> {
                            Button(
                                onClick = { showConfirmDialog = true },
                                enabled = !state.isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Text("Завершить и отправить")
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    LaunchedEffect(showEditor) {
        if (showEditor) {
            val lid = viewModel.getLevelId()
            val rid = state.requirement?.id
            if (rid != null) {
                onOpenEditor(lid, rid)
            }
            showEditor = false
        }
    }

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