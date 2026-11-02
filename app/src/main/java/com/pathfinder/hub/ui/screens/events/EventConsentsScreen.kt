package com.pathfinder.hub.ui.screens.events

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventConsentsScreen(
    onBack: () -> Unit,
    viewModel: EventConsentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Согласия родителей") },
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
                        Icons.Default.Person, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Согласия не запрошены",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Вернитесь на экран события и нажмите «Запросить согласия»",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                // Сводка
                val approved = state.items.count { it.consent.status == "approved" }
                val declined = state.items.count { it.consent.status == "declined" }
                val pending = state.items.count { it.consent.status == "pending" }
                val withDoc = state.items.count { !it.consent.documentUrl.isNullOrBlank() }

                Column(Modifier.fillMaxSize().padding(padding)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(Modifier.padding(16.dp)) {
                            StatCell("Всего", state.items.size.toString(), Modifier.weight(1f))
                            StatCell("Да", approved.toString(), Modifier.weight(1f), PathfinderGreen)
                            StatCell("Нет", declined.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.error)
                            StatCell("Ждут", pending.toString(), Modifier.weight(1f))
                        }
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 0.dp)) {
                            StatCell("С фото", withDoc.toString(), Modifier.weight(1f), PathfinderBlue)
                            Spacer(Modifier.weight(3f))
                        }
                    }

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.items, key = { "${it.consent.childId}_${it.consent.parentId}" }) { item ->
                            ConsentRow(item, fmt)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConsentRow(item: EventConsentItem, fmt: SimpleDateFormat) {
    val ctx = LocalContext.current
    val status = item.consent.status
    val statusColor = when (status) {
        "approved" -> PathfinderGreen
        "declined" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    val hasDoc = !item.consent.documentUrl.isNullOrBlank()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${item.child?.firstName ?: ""} ${item.child?.lastName ?: ""}".trim()
                            .ifBlank { item.consent.childId },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Родитель: " + (
                            "${item.parent?.firstName ?: ""} ${item.parent?.lastName ?: ""}".trim()
                                .ifBlank { item.consent.parentId }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.consent.respondedAt?.let {
                        Text(
                            "Ответ: " + fmt.format(it),
                            style = MaterialTheme.typography.labelSmall,
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
                            else if (status == "declined") Icons.Default.Close
                            else Icons.Default.Schedule,
                            null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Документ
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Description, null,
                    tint = if (hasDoc) PathfinderGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    if (hasDoc) "Документ приложен" else "Без документа",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasDoc) PathfinderGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (hasDoc) {
                    OutlinedButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.consent.documentUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                        } catch (_: Exception) { }
                    }) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Открыть")
                    }
                }
            }
        }
    }
}
