package com.pathfinder.hub.ui.screens.books

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookReaderScreen(
    onBack: () -> Unit,
    onDeleteBook: () -> Unit = onBack,
    viewModel: BookReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showBookmarks by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf<Triple<Int, Int, String?>?>(null) }
    var menuFor by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }

    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(state.scrollTarget, state.content) {
        val target = state.scrollTarget ?: return@LaunchedEffect
        val content = state.content ?: return@LaunchedEffect
        val (chIdx, pIdx) = target
        var index = 1
        for (i in 0 until chIdx) {
            index += 1 + content.chapters[i].paragraphs.size
        }
        index += 1 + pIdx
        listState.animateScrollToItem(index)
        viewModel.consumeScrollTarget()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.content?.title ?: "Чтение") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showBookmarks = true }) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Закладки")
                    }
                    if (state.content != null) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, viewModel.shareChapter(0))
                            }
                            context.startActivity(Intent.createChooser(intent, "Поделиться"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Поделиться главой")
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
        val content = state.content
        if (content == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.error ?: "Не удалось открыть книгу",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.deleteBook { onDeleteBook() }
                        }
                    ) { Text("Удалить книгу") }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(content.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(content.author,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }

            content.chapters.forEachIndexed { chIdx, chapter ->
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(chapter.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                }
                items(chapter.paragraphs.size) { pIdx ->
                    val para = chapter.paragraphs[pIdx]
                    val bookmarked = viewModel.isBookmarked(chIdx, pIdx)
                    val note = viewModel.getNote(chIdx, pIdx)
                    Column {
                        Text(
                            text = para,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { menuFor = Triple(chIdx, pIdx, para) }
                                )
                        )
                        if (bookmarked || !note.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = buildString {
                                    if (bookmarked) append("🔖 ")
                                    if (!note.isNullOrBlank()) append("📝 ").append(note)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (menuFor?.first == chIdx && menuFor?.second == pIdx) {
                            val (ci, pi, text) = menuFor!!
                            BookParagraphMenu(
                                isBookmarked = bookmarked,
                                hasNote = !note.isNullOrBlank(),
                                onDismiss = { menuFor = null },
                                onToggleBookmark = {
                                    viewModel.toggleBookmark(ci, pi, chapter.title, text)
                                    menuFor = null
                                },
                                onEditNote = {
                                    noteDialog = Triple(ci, pi, note)
                                    menuFor = null
                                },
                                onShare = {
                                    val share = viewModel.shareParagraph(ci, pi)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, share)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Поделиться"))
                                    menuFor = null
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showBookmarks) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarks = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Закладки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                if (state.bookmarks.isEmpty()) {
                    Text("Закладок пока нет. Долгое нажатие на абзац → «Добавить закладку».",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(state.bookmarks, key = { it.id }) { bm ->
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                    .combinedClickable(
                                        onClick = {
                                            showBookmarks = false
                                            viewModel.scrollTo(bm.chapter, bm.verse)
                                        },
                                        onLongClick = { }
                                    )
                            ) {
                                Text(bm.textPreview, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                if (!bm.note.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("📝 ${bm.note}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    noteDialog?.let { (ci, pi, existing) ->
        var text by remember { mutableStateOf(existing ?: "") }
        AlertDialog(
            onDismissRequest = { noteDialog = null },
            title = { Text("Заметка") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveNote(ci, pi, text.ifBlank { null })
                    noteDialog = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { noteDialog = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun BookParagraphMenu(
    isBookmarked: Boolean,
    hasNote: Boolean,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onEditNote: () -> Unit,
    onShare: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (isBookmarked) "Убрать закладку" else "Добавить закладку") },
            onClick = onToggleBookmark,
            leadingIcon = {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null
                )
            }
        )
        DropdownMenuItem(
            text = { Text(if (hasNote) "Редактировать заметку" else "Добавить заметку") },
            onClick = onEditNote,
        )
        DropdownMenuItem(
            text = { Text("Поделиться") },
            onClick = onShare,
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
        )
    }
}
