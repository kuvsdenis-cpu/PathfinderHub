package com.pathfinder.hub.ui.challenges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
fun ChallengesScreen(
    onNavigateBack: () -> Unit = {},
    onChallengeClick: (String) -> Unit = {},
    viewModel: ChallengesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Челленджи") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.challenges.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Активных челленджей пока нет", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.challenges) { item ->
                ChallengeCard(item, onClick = { onChallengeClick(item.challenge.id) })
            }
        }
    }
}

@Composable
private fun ChallengeCard(item: ChallengeWithProgress, onClick: () -> Unit) {
    val challenge = item.challenge
    val progress = item.progress

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, null, tint = PathfinderBlue, modifier = Modifier.size(32.dp))
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(challenge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(challenge.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (progress != null) {
                val progressPercent = if (challenge.goal > 0) (progress.progress.toFloat() / challenge.goal * 100).toInt().coerceIn(0, 100) else 0
                Text("Прогресс: ${progress.progress} / ${challenge.goal} ${challenge.unit}", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { progressPercent / 100f }, modifier = Modifier.fillMaxWidth(), color = PathfinderBlue)
            } else {
                Text("Цель: ${challenge.goal} ${challenge.unit}", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(8.dp))
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
            Text("С ${dateFormat.format(challenge.dateStart)} по ${dateFormat.format(challenge.dateEnd)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}