package com.pathfinder.hub.ui.screens.home.parent

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
fun ParentConsentsScreen(
    onBack: () -> Unit,
    viewModel: ParentConsentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))

    // Какой consent ждёт фото
    var pendingPhotoItem by remember { mutableStateOf<ConsentItem?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val item = pendingPhotoItem
        if (uri != null && item != null) {
            viewModel.uploadDocument(item, uri)
        }
        pendingPhotoItem = null
    }

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
                title = { Text("Согласия") },
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
                        Icons.Default.Event, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Запросов согласий нет", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { "${it.consent.eventId}_${it.consent.childId}" }) { item ->
                    val k = "${item.consent.eventId}_${item.consent.childId}"
                    val isUploading = state.uploadingKey == k
                    ConsentCard(
                        item = item,
                        fmt = fmt,
                        isUploading = isUploading,
                        onApprove = { viewModel.respond(item, true) },
                        onDecline = { viewModel.respond(item, false) },
                        onAttachPhoto = {
                            pendingPhotoItem = item
                            pickPhoto.launch("image/*")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsentCard(
    item: ConsentItem,
    fmt: SimpleDateFormat,
    isUploading: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onAttachPhoto: () -> Unit,
) {
    val status = item.consent.status
    val color = when (status) {
        "approved" -> PathfinderGreen
        "declined" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    val hasDoc = !item.consent.documentUrl.isNullOrBlank()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                "approved" -> PathfinderGreen.copy(alpha = 0.10f)
                "declined" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Event, null,
                    tint = PathfinderBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.event?.title ?: "Мероприятие",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    item.event?.dateStart?.let {
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
                        Text(when (status) {
                            "approved" -> "Согласовано"
                            "declined" -> "Отклонено"
                            else -> "Ожидает"
                        })
                    },
                    leadingIcon = {
                        Icon(
                            if (status == "approved") Icons.Default.CheckCircle
                            else Icons.Default.Schedule,
                            null,
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            item.child?.let { c ->
                Text(
                    "Ребёнок: ${c.firstName} ${c.lastName}".trim(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // ---------- Секция "Фото документа" ----------
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (hasDoc)
                        PathfinderGreen.copy(alpha = 0.10f)
                    else
                        MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Description, null,
                            tint = if (hasDoc) PathfinderGreen else PathfinderBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (hasDoc) "Документ приложен" else "Документ не приложен",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasDoc) PathfinderGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (hasDoc)
                            "Подписанное согласие загружено. Директор увидит его."
                        else
                            "Распечатайте документ, подпишите, сфотографируйте и приложите.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAttachPhoto,
                        enabled = !isUploading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp), strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Загрузка...")
                        } else {
                            Icon(
                                Icons.Default.AttachFile, null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(if (hasDoc) "Заменить фото" else "Приложить фото подписанного")
                        }
                    }
                }
            }

            if (status == "pending") {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApprove,
                        enabled = hasDoc,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Согласен")
                    }
                    OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Не согласен")
                    }
                }
                if (!hasDoc) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Кнопка «Согласен» станет активной после загрузки документа",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
