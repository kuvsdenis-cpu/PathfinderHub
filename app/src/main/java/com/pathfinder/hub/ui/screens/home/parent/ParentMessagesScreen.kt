package com.pathfinder.hub.ui.screens.home.parent

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.parent.ParentMessageEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentMessagesScreen(
    onBack: () -> Unit,
    viewModel: ParentMessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Сообщения от клуба") },
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
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.messages.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Email, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Сообщений пока нет", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.id }) { m ->
                    MessageCard(m, fmt, onReply = { viewModel.openReply(m) })
                }
            }
        }
    }

    state.replyTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { viewModel.closeReply() },
            title = { Text("Ответ") },
            text = {
                Column {
                    Text(
                        m.subject,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        m.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.replyText,
                        onValueChange = viewModel::onReplyChange,
                        label = { Text("Ваш ответ") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.sendReply() },
                    enabled = !state.isSending && state.replyText.isNotBlank(),
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Отправить")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeReply() }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun MessageCard(
    m: ParentMessageEntity,
    fmt: SimpleDateFormat,
    onReply: () -> Unit,
) {
    val hasReply = !m.response.isNullOrBlank()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasReply)
                PathfinderGreen.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Email, null,
                    tint = PathfinderBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        m.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        fmt.format(m.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(m.message, style = MaterialTheme.typography.bodyMedium)

            if (hasReply) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = PathfinderGreen.copy(alpha = 0.15f)
                    )
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "Ваш ответ",
                            style = MaterialTheme.typography.labelSmall,
                            color = PathfinderGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(m.response ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onReply) { Text("Изменить ответ") }
            } else {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onReply,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Ответить")
                }
            }
        }
    }
}
