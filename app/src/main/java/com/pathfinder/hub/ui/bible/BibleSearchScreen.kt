package com.pathfinder.hub.ui.bible

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleSearchScreen(
    onBack: () -> Unit,
    onOpenVerse: (bookId: String, chapter: Int, verseId: String) -> Unit,
    viewModel: BibleSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск по Библии") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Введите слово или фразу…") },
                singleLine = true,
            )

            when {
                state.isSearching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.hasSearched && state.results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Ничего не найдено",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.results.isNotEmpty() -> {
                    Text(
                        "Найдено: ${state.results.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(state.results, key = { it.id }) { verse ->
                            Column {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onOpenVerse(verse.bookId, verse.chapter, verse.id)
                                        }
                                        .padding(vertical = 10.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${verse.bookId.substringAfter(':')} ${verse.chapter}:${verse.verse}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        HighlightedText(
                                            text = verse.text,
                                            query = state.query,
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightedText(text: String, query: String) {
    if (query.isBlank()) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val annotated = buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var start = 0
        while (true) {
            val idx = lowerText.indexOf(lowerQuery, start)
            if (idx < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(
                SpanStyle(
                    background = androidx.compose.ui.graphics.Color(0xFFFFF176),
                    fontWeight = FontWeight.SemiBold,
                )
            ) {
                append(text.substring(idx, idx + query.length))
            }
            start = idx + query.length
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium)
}
