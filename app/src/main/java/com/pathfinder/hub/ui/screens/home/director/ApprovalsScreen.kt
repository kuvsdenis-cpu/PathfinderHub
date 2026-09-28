package com.pathfinder.hub.ui.screens.home.director

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(
    onBack: () -> Unit,
    viewModel: ApprovalsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var rejectDialogItem by remember { mutableStateOf<ApprovalItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Одобрение требований") },
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
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.errorMessage != null && state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Approval,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Нет заявок на одобрение", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(state.items, key = { "${it.userId}_${it.requirementId}" }) { item ->
                        ApprovalCard(
                            item = item,
                            isProcessing = state.processingId == "${item.userId}_${item.requirementId}",
                            onApprove = { viewModel.approve(item) },
                            onReject = { rejectDialogItem = item }
                        )
                    }
                }
            }
        }
    }

    // Диалог для ввода комментария при отклонении
    rejectDialogItem?.let { item ->
        RejectDialog(
            userName = item.userName,
            onDismiss = { rejectDialogItem = null },
            onConfirm = { comment ->
                viewModel.reject(item, comment)
                rejectDialogItem = null
            }
        )
    }
}

@Composable
private fun ApprovalCard(
    item: ApprovalItem,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (item) {
                        is ApprovalItem.Level -> Icons.Default.School
                        is ApprovalItem.Honor -> Icons.Default.Star
                    },
                    contentDescription = null,
                    tint = PathfinderBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when (item) {
                            is ApprovalItem.Level -> "Ступень: ${item.levelId}"
                            is ApprovalItem.Honor -> "Специализация: ${item.honorId}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Требование: ${item.requirementId}",
                style = MaterialTheme.typography.bodyMedium
            )

            item.submittedAt?.let { date ->
                Text(
                    text = "Подано: ${SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Отклонить")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onApprove,
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Одобрить")
                    }
                }
            }
        }
    }
}

@Composable
private fun RejectDialog(
    userName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отклонить заявку") },
        text = {
            Column {
                Text("Укажите причину отклонения заявки от $userName:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(comment) },
                enabled = comment.isNotBlank()
            ) { Text("Отклонить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}