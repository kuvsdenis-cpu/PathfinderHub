package com.pathfinder.hub.ui.screens.home.conference

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceMembersScreen(
    onBack: () -> Unit,
    viewModel: ConferenceStaffViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кадры конференции") },
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
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.members.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text("Сотрудников пока нет")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.members, key = { it.id }) { user ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person, null,
                                tint = PathfinderBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${user.firstName} ${user.lastName}".trim(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    roleLabel(user.role),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!user.clubId.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("Клуб: " + user.clubId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "director" -> "Директор"
    "instructor" -> "Наставник"
    "secretary" -> "Секретарь клуба"
    "conference_admin" -> "Администратор конференции"
    "conference_secretary" -> "Секретарь конференции"
    else -> role
}
