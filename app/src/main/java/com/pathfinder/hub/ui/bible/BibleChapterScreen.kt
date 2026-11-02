package com.pathfinder.hub.ui.bible

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться"))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BibleChapterScreen(
    onBack: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenChapter: (String, Int) -> Unit,
    viewModel: BibleChapterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val ctx = LocalContext.current

    // === Выделение отрывка ===
    var selectionStart by remember { mutableStateOf<Int?>(null) }
    var selectionEnd by remember { mutableStateOf<Int?>(null) }
    val isSelecting = selectionStart != null

    fun clearSelection() {
        selectionStart = null
        selectionEnd = null
    }

    fun toggleVerseSelection(verseNumber: Int) {
        val start = selectionStart
        val end = selectionEnd
        when {
            start == null -> {
                selectionStart = verseNumber
                selectionEnd = verseNumber
            }
            end == null -> {
                selectionStart = minOf(start, verseNumber)
                selectionEnd = maxOf(start, verseNumber)
            }
            verseNumber < start -> selectionStart = verseNumber
            verseNumber > end -> selectionEnd = verseNumber
            else -> clearSelection()
        }
    }

    // Автоскролл к стиху, если пришёл verseId.
    // Ключи — verses И verseId: сработает, когда оба готовы.
    // После скролла VM сбрасывает verseId через consumeScrollTarget(),
    // поэтому повторный recomposition не вызовет повторный скролл.
    LaunchedEffect(state.verses, state.verseId) {
        val target = state.verseId ?: return@LaunchedEffect
        if (state.verses.isEmpty()) return@LaunchedEffect
        val index = state.verses.indexOfFirst { it.id == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            viewModel.consumeScrollTarget()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = if (state.bookName.isNotEmpty())
                        "${state.bookName} ${state.chapterNumber}"
                    else "Глава ${state.chapterNumber}"
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelecting) clearSelection() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (isSelecting) {
                        IconButton(onClick = {
                            val s = selectionStart!!
                            val e = selectionEnd ?: s
                            val header = if (state.bookName.isNotEmpty())
                                "${state.bookName} ${state.chapterNumber}:$s-$e"
                            else "${state.chapterNumber}:$s-$e"
                            val body = state.verses
                                .filter { it.verse in s..e }
                                .joinToString("\n") { "${it.verse}. ${it.text}" }
                            shareText(ctx, "$header\n\n$body")
                            clearSelection()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Поделиться отрывком")
                        }
                        IconButton(onClick = { clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Отменить выделение")
                        }
                    } else {
                        IconButton(onClick = {
                            val header = if (state.bookName.isNotEmpty())
                                "${state.bookName} ${state.chapterNumber}"
                            else "Глава ${state.chapterNumber}"
                            val body = state.verses.joinToString("\n") { v ->
                                "${v.verse}. ${v.text}"
                            }
                            shareText(ctx, "$header\n\n$body")
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Поделиться главой")
                        }
                        IconButton(onClick = onOpenBookmarks) {
                            Icon(Icons.Default.Bookmark, contentDescription = "Закладки")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            itemsIndexed(state.verses, key = { _, v -> v.id }) { _, verse ->
                val isBookmarked = verse.id in state.bookmarkedVerseIds
                val s = selectionStart
                val e = selectionEnd
                val inSelection = s != null && e != null &&
                        verse.verse in minOf(s, e)..maxOf(s, e)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(
                            if (inSelection) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .combinedClickable(
                            onClick = {
                                if (isSelecting) toggleVerseSelection(verse.verse)
                            },
                            onLongClick = {
                                if (!isSelecting) {
                                    selectionStart = verse.verse
                                    selectionEnd = verse.verse
                                } else {
                                    toggleVerseSelection(verse.verse)
                                }
                            },
                        ),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = verse.verse.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp).padding(top = 4.dp),
                    )
                    Text(
                        text = verse.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(8.dp))
                    IconButton(
                        onClick = { viewModel.toggleBookmark(verse) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (isBookmarked)
                                Icons.Default.Bookmark
                            else
                                Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Убрать закладку" else "Добавить закладку",
                        )
                    }
                }
            }

            if (state.hasNextChapter) {
                item {
                    Spacer(Modifier.size(16.dp))
                    TextButton(
                        onClick = {
                            val next = state.chapterNumber + 1
                            onOpenChapter(viewModel.bookIdPublic(), next)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("→ Следующая глава")
                    }
                }
            }
        }
    }
}
