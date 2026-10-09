package com.pathfinder.hub.ui.challenges

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pathfinder.hub.ui.share.ShareCardGenerator
import com.pathfinder.hub.ui.share.ShareCardHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeDetailScreen(
    challengeId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: ChallengeDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(challengeId) {
        viewModel.loadChallenge(challengeId)
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Челлендж") },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val challenge = state.challenge
        if (challenge == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Челлендж не найден")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.EmojiEvents, null, tint = PathfinderBlue, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(challenge.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(challenge.description, style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.height(24.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Тип: ${challenge.type}", style = MaterialTheme.typography.bodyMedium)
                    Text("Цель: ${challenge.goal} ${challenge.unit}", style = MaterialTheme.typography.bodyMedium)
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
                    Text("Период: ${dateFormat.format(challenge.dateStart)} - ${dateFormat.format(challenge.dateEnd)}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(24.dp))

            val progress = state.progress
            if (progress != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Ваш прогресс", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        val progressPercent = if (challenge.goal > 0) (progress.progress.toFloat() / challenge.goal * 100).toInt().coerceIn(0, 100) else 0

                        Text("${progress.progress} / ${challenge.goal} ${challenge.unit}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progressPercent / 100f }, modifier = Modifier.fillMaxWidth(), color = PathfinderBlue)

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.updateProgress(progress.progress + 1) },
                            enabled = !state.isUpdatingProgress && progress.progress < challenge.goal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isUpdatingProgress) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Обновить прогресс (+1)")
                            }
                        }

                        // ---------- Завершение челленджа ----------
                        val isCompleted = progress.completedAt != null
                        val isRewardClaimed = progress.rewardClaimed

                        if (isCompleted) {
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = PathfinderBlue.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "🏆 Челлендж завершён!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (challenge.rewards.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Призы: " + challenge.rewards.joinToString(", "),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        if (isCompleted && !isRewardClaimed) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.claimReward() },
                                enabled = !state.isUpdatingProgress,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Получить приз")
                            }
                        } else if (isRewardClaimed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Приз получен",
                                style = MaterialTheme.typography.bodySmall,
                                color = PathfinderBlue
                            )
                        }

                        // ---------- Поделиться ----------
                        val ctx = LocalContext.current
                        val scope = rememberCoroutineScope()
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        ShareCardGenerator.generate(
                                            context = ctx,
                                            emoji = "\uD83C\uDFC6",
                                            title = challenge.title,
                                            subtitle = progress.progress.toString() + " / " +
                                                    challenge.goal + " " + challenge.unit,
                                            userName = null,
                                        )
                                    }
                                    ShareCardHelper.share(ctx, file, "Мой челлендж в Pathfinder Hub")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Поделиться")
                        }
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.joinChallenge() },
                    enabled = !state.isJoining,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isJoining) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Присоединиться к челленджу")
                    }
                }
            }
        }
    }
}