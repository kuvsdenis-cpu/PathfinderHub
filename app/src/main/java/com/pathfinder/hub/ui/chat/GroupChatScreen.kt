package com.pathfinder.hub.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import com.pathfinder.hub.ui.comments.CommentsSection
import com.pathfinder.hub.ui.storage.FileUploadResult
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    targetType: String,
    targetId: String,
    onBack: () -> Unit,
    savedStateHandle: SavedStateHandle,
    viewModel: GroupChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val pendingAttachment by savedStateHandle
        .getStateFlow<FileUploadResult?>(FileUploadResult.SAVED_STATE_KEY, null)
        .collectAsState()

    LaunchedEffect(targetType, targetId) {
        viewModel.init(targetType, targetId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Групповой чат", fontWeight = FontWeight.Bold)
                        Text(
                            text = state.groupName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (state.clubId != null) {
                        IconButton(onClick = {
                            // TODO: onAttachFile — если нужно
                        }) {
                            Icon(Icons.Default.AttachFile, "Прикрепить файл")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.errorMessage != null -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            state.clubId != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = PathfinderBlue.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Group,
                                null,
                                tint = PathfinderBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Групповой чат",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Все участники группы видят эти сообщения. Модерация включена.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    CommentsSection(
                        targetType = targetType,
                        targetId = targetId,
                        clubId = state.clubId!!,
                        modifier = Modifier.weight(1f),
                        pendingAttachment = pendingAttachment,
                        onAttachmentConsumed = {
                            savedStateHandle.remove<FileUploadResult>(FileUploadResult.SAVED_STATE_KEY)
                        }
                    )
                }
            }
        }
    }
}