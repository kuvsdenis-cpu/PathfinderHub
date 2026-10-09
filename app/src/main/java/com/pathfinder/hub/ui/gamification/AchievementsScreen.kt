package com.pathfinder.hub.ui.gamification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import com.pathfinder.hub.ui.share.ShareCardGenerator
import com.pathfinder.hub.ui.share.ShareCardHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Достижения") },
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
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (state.achievements.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Достижений пока нет",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = PathfinderBlue.copy(alpha = 0.1f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Получено: ${state.earnedCount} из ${state.totalCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PathfinderBlue
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (state.totalCount == 0) 0f
                                else state.earnedCount.toFloat() / state.totalCount
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val grouped = state.achievements.groupBy { it.achievement.category }
            val categoryTitles = mapOf(
                "learning" to "Обучение",
                "requirements" to "Требования",
                "tests" to "Тесты",
                "service" to "Служение",
                "spiritual" to "Духовный рост",
                "social" to "Общение",
                "chat" to "Чат"
            )

            for ((category, items) in grouped) {
                item(key = "header_$category") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = categoryTitles[category] ?: category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PathfinderBlue
                    )
                }
                items(items, key = { it.achievement.id }) { item ->
                    AchievementCard(item)
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(item: AchievementWithProgress) {
    val achievement = item.achievement
    val isEarned = item.progress?.status == "earned"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEarned) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = achievement.icon,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isEarned) PathfinderBlue else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isEarned) {
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                IconButton(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ShareCardGenerator.generate(
                                context = ctx,
                                emoji = achievement.icon,
                                title = achievement.name,
                                subtitle = achievement.description,
                                userName = null,
                            )
                        }
                        ShareCardHelper.share(ctx, file, "Моё достижение в Pathfinder Hub: " + achievement.name)
                    }
                }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Поделиться",
                        tint = PathfinderBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Заблокировано",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
