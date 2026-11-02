package com.pathfinder.hub.ui.pearls

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PearlTrainingScreen(
    onBack: () -> Unit,
    viewModel: PearlTrainingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тренировка") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorView(state.error!!, onBack)
                state.finished -> FinishView(state, onRetry = viewModel::onRetry, onExit = onBack)
                else -> TaskView(state, viewModel)
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack) { Text("Назад") }
    }
}

@Composable
private fun FinishView(
    state: PearlTrainingUiState,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Результат", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "${state.correctCount} из ${state.total} (${state.scorePercent}%)",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (state.isSaving) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Сохранение…", style = MaterialTheme.typography.bodySmall)
            }
        }
        state.saveError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Ещё раз") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Выход") }
    }
}

@Composable
private fun TaskView(state: PearlTrainingUiState, viewModel: PearlTrainingViewModel) {
    val task = state.currentTask ?: return
    val answer = state.userAnswers.getOrNull(state.currentIndex).orEmpty()

    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { state.progressFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Задание ${state.currentIndex + 1} из ${state.total}",
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(modeTitle(task.mode), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        when (task.mode) {
            TrainingMode.FILL_BLANKS -> FillBlanksView(task, answer, state.submitted) {
                viewModel.onAnswerChanged(it)
            }
            TrainingMode.FIRST_LETTERS -> FirstLettersView(task, answer, state.submitted) {
                viewModel.onAnswerChanged(it)
            }
            TrainingMode.CHOOSE_WORD -> ChooseWordView(task, answer, state.submitted) {
                viewModel.onAnswerChanged(it)
            }
            TrainingMode.ASSEMBLE -> AssembleView(task.shuffled, answer, state.submitted) {
                viewModel.onAnswerChanged(it)
            }
            TrainingMode.CHUNKS -> ChunksView(task.shuffled, answer, state.submitted) {
                viewModel.onAnswerChanged(it)
            }
        }

        Spacer(Modifier.weight(1f))
        if (!state.submitted) {
            Button(
                onClick = viewModel::onSubmit,
                enabled = answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Проверить") }
        } else {
            Button(onClick = viewModel::onNext, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.currentIndex + 1 >= state.total) "Завершить" else "Далее")
            }
        }
    }
}

@Composable
private fun FillBlanksView(
    task: TrainingTask,
    answer: String,
    submitted: Boolean,
    onAnswer: (String) -> Unit,
) {
    Text(
        "Впиши пропущенные слова через пробел:",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        task.words.mapIndexed { i, w ->
            if (task.blanks.any { it.wordIndex == i }) "____" else w
        }.joinToString(" "),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = answer,
        onValueChange = onAnswer,
        enabled = !submitted,
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
    )
}

@Composable
private fun FirstLettersView(
    task: TrainingTask,
    answer: String,
    submitted: Boolean,
    onAnswer: (String) -> Unit,
) {
    Text("Впиши слова по первым буквам:", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        task.words.mapIndexed { i, w ->
            val blank = task.blanks.firstOrNull { it.wordIndex == i }
            if (blank != null) "${w.first()}…" else w
        }.joinToString(" "),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = answer,
        onValueChange = onAnswer,
        enabled = !submitted,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChooseWordView(
    task: TrainingTask,
    answer: String,
    submitted: Boolean,
    onAnswer: (String) -> Unit,
) {
    Text("Выбери пропущенное слово:", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        task.words.mapIndexed { i, w ->
            if (task.blanks.any { it.wordIndex == i }) "____" else w
        }.joinToString(" "),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(12.dp))
    val options = task.blanks.firstOrNull()?.options.orEmpty()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { opt ->
            OutlinedButton(
                onClick = { onAnswer(opt) },
                enabled = !submitted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (answer == opt) "✓ $opt" else opt)
            }
        }
    }
}

@Composable
private fun AssembleView(
    shuffled: List<String>,
    answer: String,
    submitted: Boolean,
    onAnswer: (String) -> Unit,
) {
    Text("Собери стих из слов:", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    Text(shuffled.joinToString(" "), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = answer,
        onValueChange = onAnswer,
        enabled = !submitted,
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
    )
}

@Composable
private fun ChunksView(
    chunks: List<String>,
    answer: String,
    submitted: Boolean,
    onAnswer: (String) -> Unit,
) {
    Text("Собери стих из фрагментов:", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chunks) { c ->
            OutlinedButton(
                onClick = { onAnswer(if (answer.isBlank()) c else "$answer $c") },
                enabled = !submitted,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(c) }
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = answer,
        onValueChange = onAnswer,
        enabled = !submitted,
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
    )
}

private fun modeTitle(mode: TrainingMode): String = when (mode) {
    TrainingMode.FILL_BLANKS -> "Заполни пропуски"
    TrainingMode.ASSEMBLE -> "Собери стих"
    TrainingMode.CHOOSE_WORD -> "Выбери слово"
    TrainingMode.FIRST_LETTERS -> "По первым буквам"
    TrainingMode.CHUNKS -> "Собери из фрагментов"
}
