package com.pathfinder.hub.ui.screens.club_info

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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

private const val EMPTY = "Не указано"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubInfoScreen(
    onBack: () -> Unit,
    viewModel: ClubInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("О клубе") },
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
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center
            ) { CircularProgressIndicator() }

            state.club == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Text(
                    state.errorMessage ?: "Клуб не найден",
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> {
                val c = state.club!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Заголовок
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                c.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!c.motto.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Star, null,
                                        tint = PathfinderBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        c.motto,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = PathfinderBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Локация
                    SectionTitle("Где мы находимся")
                    InfoRow(
                        icon = Icons.Default.LocationOn,
                        label = "Город / регион",
                        value = listOfNotNull(
                            c.city.takeIf { it.isNotBlank() },
                            c.region.takeIf { it.isNotBlank() }
                        ).joinToString(", ").ifBlank { EMPTY },
                    )
                    InfoRow(
                        icon = Icons.Default.Place,
                        label = "Место проведения",
                        value = c.location.ifBlank { EMPTY },
                    )

                    // Расписание
                    SectionTitle("Расписание встреч")
                    InfoRow(
                        icon = Icons.Default.AccessTime,
                        label = "День",
                        value = c.meetingDay.ifBlank { EMPTY },
                    )
                    InfoRow(
                        icon = Icons.Default.AccessTime,
                        label = "Время",
                        value = buildString {
                            if (c.startTime.isBlank() && c.endTime.isBlank()) {
                                append(EMPTY)
                            } else {
                                append(c.startTime.ifBlank { "?" })
                                append(" — ")
                                append(c.endTime.ifBlank { "?" })
                            }
                        },
                    )
                    InfoRow(
                        icon = Icons.Default.AccessTime,
                        label = "Частота",
                        value = c.frequency.ifBlank { EMPTY },
                    )

                    // Контакты
                    SectionTitle("Контакты")
                    InfoRow(
                        icon = Icons.Default.Email,
                        label = "Email",
                        value = c.contactEmail.ifBlank { EMPTY },
                    )
                    InfoRow(
                        icon = Icons.Default.Phone,
                        label = "Телефон",
                        value = c.contactPhone.ifBlank { EMPTY },
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = PathfinderBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
