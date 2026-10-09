package com.pathfinder.hub.ui.gamification

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalUniformScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DigitalUniformViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Цифровая форма") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Share */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться")
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
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val uniform = state.uniform
        if (uniform == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Форма пока не сформирована",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UniformAvatar(
                        shirtColor = uniform.shirtColor,
                        tieColor = uniform.tieColor,
                        badgesCount = uniform.earnedBadges.size,
                        chevronsCount = uniform.earnedChevrons.size,
                        marksCount = uniform.specialMarks.size
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Твоя цифровая форма",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            UniformSection(
                title = "Нашивки",
                subtitle = "Специализации",
                count = uniform.earnedBadges.size,
                icon = Icons.Default.Star
            ) {
                if (uniform.earnedBadges.isEmpty()) {
                    EmptySectionText("Начни изучать специализации!")
                } else {
                    uniform.earnedBadges.forEach { badge ->
                        UniformItem(
                            title = "Специализация",
                            subtitle = "ID: ${badge.honorId}",
                            earnedDate = badge.earnedAt
                        )
                    }
                }
            }

            UniformSection(
                title = "Шевроны",
                subtitle = "Ступени",
                count = uniform.earnedChevrons.size,
                icon = Icons.Default.Person
            ) {
                if (uniform.earnedChevrons.isEmpty()) {
                    EmptySectionText("Пройди первую ступень!")
                } else {
                    uniform.earnedChevrons.forEach { chevron ->
                        UniformItem(
                            title = "Ступень",
                            subtitle = "ID: ${chevron.levelId}",
                            earnedDate = chevron.earnedAt
                        )
                    }
                }
            }

            UniformSection(
                title = "Особые знаки",
                subtitle = "Достижения",
                count = uniform.specialMarks.size,
                icon = Icons.Default.Star
            ) {
                if (uniform.specialMarks.isEmpty()) {
                    EmptySectionText("Получи первое достижение!")
                } else {
                    uniform.specialMarks.forEach { mark ->
                        UniformItem(
                            title = "Достижение",
                            subtitle = mark.icon,
                            earnedDate = mark.earnedAt
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UniformAvatar(
    shirtColor: String,
    tieColor: String,
    badgesCount: Int,
    chevronsCount: Int,
    marksCount: Int
) {
    Canvas(modifier = Modifier.size(200.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        drawCircle(color = Color(0xFFFFDBB5), radius = 40f, center = Offset(centerX, centerY - 60f))
        drawRect(color = parseColor(shirtColor), topLeft = Offset(centerX - 50f, centerY - 20f), size = Size(100f, 120f))
        drawRect(color = parseColor(tieColor), topLeft = Offset(centerX - 10f, centerY - 20f), size = Size(20f, 80f))

        for (i in 0 until badgesCount.coerceAtMost(3)) {
            drawCircle(color = PathfinderBlue, radius = 8f, center = Offset(centerX - 35f, centerY + 10f + i * 20f))
        }
        for (i in 0 until chevronsCount.coerceAtMost(3)) {
            drawCircle(color = Color(0xFFFFD700), radius = 8f, center = Offset(centerX + 35f, centerY + 10f + i * 20f))
        }
        for (i in 0 until marksCount.coerceAtMost(3)) {
            drawCircle(color = Color(0xFFFF6B6B), radius = 8f, center = Offset(centerX - 30f + i * 30f, centerY - 40f), style = Stroke(width = 2f))
        }
    }
}

@Composable
private fun UniformSection(
    title: String,
    subtitle: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = PathfinderBlue)
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text(text = "$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PathfinderBlue)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun UniformItem(title: String, subtitle: String, earnedDate: java.util.Date) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = PathfinderBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(earnedDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptySectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

private fun parseColor(colorString: String): Color {
    return try {
        if (colorString.startsWith("#")) {
            Color(android.graphics.Color.parseColor(colorString))
        } else {
            when (colorString.lowercase()) {
                "blue" -> PathfinderBlue
                "red" -> Color.Red
                "green" -> Color.Green
                "yellow" -> Color.Yellow
                else -> PathfinderBlue
            }
        }
    } catch (e: Exception) {
        PathfinderBlue
    }
}