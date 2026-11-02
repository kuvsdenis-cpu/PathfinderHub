package com.pathfinder.hub.ui.sabbath

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.data.remote.dto.sabbath.*

@Composable
fun SabbathLessonScreen(
    viewModel: SabbathSchoolViewModel,
    lessonId: String
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(lessonId) {
        viewModel.loadLesson(lessonId)
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val lesson = state.lesson ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(lesson.title, style = MaterialTheme.typography.headlineMedium)
            Text(lesson.introduction, style = MaterialTheme.typography.bodyLarge)
        }

        item {
            MemoryVerseSection(
                verse = lesson.memory_verse,
                savedAnswers = state.answers,
                onAnswerChanged = { blockId, text ->
                    viewModel.onAnswerChanged(lessonId, blockId, text)
                }
            )
        }

        lesson.sections.forEach { section ->
            item {
                Text(
                    text = section.title,
                    color = Color(0xFFD81B60), 
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            items(section.blocks) { block ->
                when (block.type) {
                    "text" -> {
                        Text(
                            text = block.content ?: "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    "input_text" -> {
                        val blockId = block.id ?: ""
                        val savedText = state.answers[blockId] ?: ""
                        OutlinedTextField(
                            value = savedText,
                            onValueChange = { viewModel.onAnswerChanged(lessonId, blockId, it) },
                            placeholder = { Text(block.placeholder ?: "Твой ответ...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryVerseSection(
    verse: MemoryVerseDto,
    savedAnswers: Map<String, String>,
    onAnswerChanged: (String, String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("ПАМЯТНЫЙ СТИХ", color = Color(0xFFD81B60), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            
            Text(verse.text)
            
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                verse.answers.forEachIndexed { index, _ ->
                    val blockId = "verse_blank_$index"
                    val saved = savedAnswers[blockId] ?: ""
                    OutlinedTextField(
                        value = saved,
                        onValueChange = { onAnswerChanged(blockId, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(verse.reference, fontStyle = FontStyle.Italic, modifier = Modifier.align(androidx.compose.ui.Alignment.End))
        }
    }
}
