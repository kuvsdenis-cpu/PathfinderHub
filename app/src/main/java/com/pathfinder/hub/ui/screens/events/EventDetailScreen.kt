package com.pathfinder.hub.ui.screens.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.qr.EntityQrHelper
import com.pathfinder.hub.ui.share.ShareCardGenerator
import com.pathfinder.hub.ui.share.ShareCardHelper
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.screens.tests.TestsForScopeSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onOpenConsents: (String) -> Unit = {},
    onOpenTest: (testId: String) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru")) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.consentMessage) {
        state.consentMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Событие") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    val ctx = LocalContext.current
                    val scope = rememberCoroutineScope()
                    val event = state.event
                    if (event != null) {
                        IconButton(onClick = {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    ShareCardGenerator.generate(
                                        context = ctx,
                                        emoji = "\uD83D\uDCC5",
                                        title = event.title,
                                        subtitle = event.location.ifBlank { "Событие клуба" },
                                        userName = null,
                                        dateText = fmt.format(event.dateStart),
                                    )
                                }
                                ShareCardHelper.share(ctx, file, "Событие в Pathfinder Hub: " + event.title)
                            }
                        }) {
                            Icon(Icons.Default.Share, "Поделиться")
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
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        val e = state.event ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(e.title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "Дата: " + fmt.format(e.dateStart),
                style = MaterialTheme.typography.bodyLarge
            )
            if (e.location.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Место: " + e.location,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (e.description.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(e.description, style = MaterialTheme.typography.bodyLarge)
            }

            // ---------- Архив (только director / co-director с правами) ----------
            val staffUser = state.currentUser
            val canArchive = com.pathfinder.hub.domain.permissions.PermissionChecker
                .canArchive(staffUser)
            if (canArchive) {
                Spacer(Modifier.height(16.dp))
                if (viewModel.isArchived()) {
                    Button(
                        onClick = { viewModel.restoreEvent() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Восстановить из архива")
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.archiveEvent() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("В архив")
                    }
                }
            }

            // ---------- QR-код события ----------
            Spacer(Modifier.height(16.dp))
            val qrCtx = LocalContext.current
            val qrScope = rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val payload = viewModel.qrPayload() ?: return@OutlinedButton
                        val filename = viewModel.qrFilename() ?: "event_qr.png"
                        qrScope.launch {
                            val file = withContext(Dispatchers.IO) {
                                EntityQrHelper.generatePngForPayload(qrCtx, payload, filename)
                            }
                            EntityQrHelper.open(qrCtx, file)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.QrCode, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Показать QR")
                }
                IconButton(onClick = {
                    val payload = viewModel.qrPayload() ?: return@IconButton
                    val filename = viewModel.qrFilename() ?: "event_qr.png"
                    qrScope.launch {
                        val file = withContext(Dispatchers.IO) {
                            EntityQrHelper.generatePngForPayload(qrCtx, payload, filename)
                        }
                        EntityQrHelper.share(qrCtx, e.title, file)
                    }
                }) {
                    Icon(Icons.Default.Share, "Поделиться QR")
                }
            }

            // ---------- A2: Ваш статус ----------
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Ваш статус",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    val st = state.myParticipant?.status ?: "invited"
                    val label = when (st) {
                        "invited"   -> "Приглашение получено"
                        "accepted"  -> "Вы идёте"
                        "declined"  -> "Вы отказались"
                        "attended"  -> "Вы посетили"
                        "missed"    -> "Пропущено"
                        else        -> st
                    }
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    if (state.myParticipant != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.acceptInvite() },
                                enabled = st != "accepted" && st != "attended",
                                modifier = Modifier.weight(1f)
                            ) { Text("Приду") }
                            OutlinedButton(
                                onClick = { viewModel.declineInvite() },
                                enabled = st != "declined",
                                modifier = Modifier.weight(1f)
                            ) { Text("Не приду") }
                        }
                    }
                }
            }

            // ---------- Тесты события ----------
            TestsForScopeSection(
                scope = "event",
                scopeId = e.id,
                onOpenTest = onOpenTest,
            )

            // ---------- A2/A3: Участники события (для staff) ----------
            if (state.canManageAttendance && state.participantRows.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Участники (${state.participantRows.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        state.participantRows.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    row.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${row.statusEmoji} ${row.statusLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.markAttended(row.userId) },
                                    enabled = row.status != "attended"
                                ) { Text("Отметить") }
                            }
                        }
                    }
                }
            }

            // ---------- A3: Встреча с родителями (только staff) ----------
            val meeting = state.parentMeeting
            if (state.canManageAttendance && meeting != null) {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Встреча с родителями",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        if (meeting.agenda.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Повестка: ${meeting.agenda}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val accepted = state.attendeeRows.count { it.status == "accepted" || it.status == "attended" }
                        val declined = state.attendeeRows.count { it.status == "declined" }
                        val invited = state.attendeeRows.count { it.status == "invited" }
                        Text(
                            "Придут: $accepted · Не смогут: $declined · Не ответили: $invited",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        state.attendeeRows.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    row.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${row.statusEmoji} ${row.statusLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ---------- A3: Вопросы родителей (только staff) ----------
            if (state.canManageAttendance && state.parentQuestions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Вопросы родителей (${state.parentQuestions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        state.parentQuestions.forEach { q ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    q.parentName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    q.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // ---------- Согласия (только для staff клуба) ----------
            val u = state.currentUser
            val canRequest = u != null && (
                u.role in listOf("director", "secretary", "instructor")
                || u.isOwnerClub
            ) && !e.clubId.isNullOrBlank()

            if (canRequest) {
                Spacer(Modifier.height(24.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Согласия родителей",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Запрос будет отправлен родителям всех следопытов клуба. " +
                                    "Уже ответившие не получат повторно.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::requestConsents,
                            enabled = !state.isRequesting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isRequesting) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Отправка...")
                            } else {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Запросить согласия у родителей")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onOpenConsents(e.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Description, null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Посмотреть согласия и фото")
                        }
                    }
                }
            }
        }
    }
}
