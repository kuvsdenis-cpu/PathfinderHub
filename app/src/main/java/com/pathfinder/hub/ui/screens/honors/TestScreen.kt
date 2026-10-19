package com.pathfinder.hub.ui.screens.honors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderRed

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: TestViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тест") },
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
            state.isFinished -> ResultScreen(state, onFinished, padding)
            state.questions.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center
            ) { Text("Вопросы для этой специализации ещё не добавлены") }
            else -> QuestionView(state, viewModel, padding)
        }
    }
}

@Composable
private fun ResultScreen(
    state: TestUiState,
    onFinished: () -> Unit,
    scaffoldPadding: PaddingValues,
) {
    Column(
        Modifier.fillMaxSize().padding(scaffoldPadding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val title = when {
            state.passed -> "Тест пройден!"
            state.pendingReview -> "Тест отправлен на проверку"
            else -> "Тест не пройден"
        }
        val titleColor = when {
            state.passed -> PathfinderGreen
            state.pendingReview -> PathfinderBlue
            else -> PathfinderRed
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
        Spacer(Modifier.height(16.dp))
        Text("Результат: ${state.score}%",
            style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Проходной балл: ${state.passScore}%",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Вернуться")
        }
    }
}

@Composable
private fun QuestionView(
    state: TestUiState,
    viewModel: TestViewModel,
    scaffoldPadding: PaddingValues,
) {
    val q = state.questions[state.currentIndex]
    val selected = state.answers[q.id]

    Column(
        Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        LinearProgressIndicator(
            progress = { (state.currentIndex + 1).toFloat() / state.questions.size },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Вопрос ${state.currentIndex + 1} из ${state.questions.size}",
            style = MaterialTheme.typography.labelLarge, color = PathfinderBlue)
        Spacer(Modifier.height(16.dp))
        Text(q.text, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        if (q.type == "long_text") {
            OutlinedTextField(
                value = selected ?: "",
                onValueChange = { viewModel.selectAnswer(q.id, it) },
                label = { Text("Ваш ответ") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
        } else {
            q.options.forEach { option ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable { viewModel.selectAnswer(q.id, option) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected == option)
                            PathfinderBlue.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selected == option,
                            onClick = { viewModel.selectAnswer(q.id, option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.currentIndex > 0) {
                OutlinedButton(
                    onClick = viewModel::previous,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("Назад") }
            }
            val canProceed = when (q.type) {
                "long_text" -> !selected.isNullOrBlank()
                else -> selected != null
            }
            Button(
                onClick = viewModel::next,
                enabled = canProceed,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text(if (state.currentIndex < state.questions.lastIndex) "Далее" else "Завершить")
            }
        }
    }
}
