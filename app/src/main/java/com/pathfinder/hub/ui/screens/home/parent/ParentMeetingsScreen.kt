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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentMeetingsScreen(
    onBack: () -> Unit,
    viewModel: ParentMeetingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))
    var questionForEvent by remember { mutableStateOf<String?>(null) }
    var questionText by remember { mutableStateOf("") }

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
                title = { Text("Встречи с родителями") },
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
            state.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CalendarMonth, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Встреч пока нет", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Директор пригласит вас через приложение",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.meeting.eventId }) { item ->
                    MeetingCard(
                        item = item,
                        fmt = fmt,
                        onConfirm = { viewModel.confirm(item.meeting.eventId, true) },
                        onDecline = { viewModel.confirm(item.meeting.eventId, false) },
                        onAskQuestion = {
                            questionForEvent = item.meeting.eventId
                            questionText = ""
                        },
                    )
                }
            }
        }
    }

    questionForEvent?.let { eventId ->
        AlertDialog(
            onDismissRequest = { questionForEvent = null },
            title = { Text("Задать вопрос") },
            text = {
                Column {
                    Text("Вопрос будет виден директору и другим родителям")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it },
                        label = { Text("Ваш вопрос") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addQuestion(eventId, questionText)
                        questionForEvent = null
                    },
                    enabled = questionText.isNotBlank(),
                ) { Text("Отправить") }
            },
            dismissButton = {
                TextButton(onClick = { questionForEvent = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun MeetingCard(
    item: ParentMeetingItem,
    fmt: SimpleDateFormat,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onAskQuestion: () -> Unit,
) {
    val confirmed = item.attendee?.confirmed == true
    val event = item.event

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (confirmed)
                PathfinderGreen.copy(alpha = 0.10f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarMonth, null,
                    tint = PathfinderBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        event?.title ?: "Встреча",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    event?.dateStart?.let {
                        Text(
                            fmt.format(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (confirmed) "Вы придёте" else "Не подтверждено")
                    },
                )
            }

            if (item.meeting.agenda.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Повестка: " + item.meeting.agenda,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            val myQuestions = item.attendee?.questions ?: emptyList()
            if (myQuestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ваши вопросы:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                myQuestions.forEach { q ->
                    Text("• $q", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!confirmed) {
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Буду")
                    }
                } else {
                    OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Не смогу")
                    }
                }
                OutlinedButton(onClick = onAskQuestion, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Help, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Вопрос")
                }
            }
        }
    }
}
