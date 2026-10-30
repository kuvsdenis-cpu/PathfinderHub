package com.pathfinder.hub.ui.sabbath

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Список уроков Субботней школы для выбранного года/квартала.
 * lessonId = "${year}${quarter}_%02d" (например, "A3_01", "B4_07").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SabbathSchoolListScreen(
    year: String,
    quarter: Int,
    currentLesson: Int?,
    onLessonClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val yearLabel = when (year) {
        "A" -> "А"
        "B" -> "Б"
        "V" -> "В"
        "G" -> "Г"
        else -> year
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Субботняя школа") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Настройки") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Год $yearLabel, квартал $quarter",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            items((1..13).toList()) { lessonNum ->
                val lessonId = "%s%d_%02d".format(year, quarter, lessonNum)
                val isCurrent = currentLesson == lessonNum
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLessonClick(lessonId) },
                    colors = if (isCurrent) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) else CardDefaults.cardColors(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Урок $lessonNum",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (isCurrent) {
                            Text(
                                "сейчас",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD81B60),
                            )
                        }
                    }
                }
            }
        }
    }
}
