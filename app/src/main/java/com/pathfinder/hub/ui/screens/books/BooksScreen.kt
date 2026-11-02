package com.pathfinder.hub.ui.screens.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.learning.BookEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    onBack: () -> Unit,
    onOpenBook: (bookId: String) -> Unit,
    onCreateBook: () -> Unit = {},
    viewModel: BooksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var editBook by remember { mutableStateOf<BookEntity?>(null) }
    var deleteBook by remember { mutableStateOf<BookEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Книги") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (state.canEditBook) {
                        IconButton(onClick = { viewModel.deleteBrokenBooks() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить битые")
                        }
                    }
                    if (state.canCreateBook) {
                        IconButton(onClick = onCreateBook) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить книгу")
                        }
                    }
                }
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (state.books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Книг пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.books, key = { it.id }) { book ->
                BookRow(
                    book = book,
                    canEdit = state.canEditBook && book.clubId != null,
                    onClick = { onOpenBook(book.id) },
                    onEdit = { editBook = book },
                    onDelete = { deleteBook = book },
                )
            }
        }
    }

    // ---------- Edit dialog ----------
    editBook?.let { b ->
        var t by remember(b.id) { mutableStateOf(b.title) }
        var a by remember(b.id) { mutableStateOf(b.author) }
        var d by remember(b.id) { mutableStateOf(b.description ?: "") }
        var c by remember(b.id) { mutableStateOf(b.coverUrl ?: "") }
        AlertDialog(
            onDismissRequest = { editBook = null },
            title = { Text("Редактировать книгу") },
            text = {
                Column {
                    OutlinedTextField(value = t, onValueChange = { t = it },
                        label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = a, onValueChange = { a = it },
                        label = { Text("Автор") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = d, onValueChange = { d = it },
                        label = { Text("Описание") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    OutlinedTextField(value = c, onValueChange = { c = it },
                        label = { Text("Обложка (URL)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = t.isNotBlank() && a.isNotBlank(),
                    onClick = {
                        viewModel.updateBook(b, t, a, d, c)
                        editBook = null
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editBook = null }) { Text("Отмена") } }
        )
    }

    // ---------- Delete dialog ----------
    deleteBook?.let { b ->
        AlertDialog(
            onDismissRequest = { deleteBook = null },
            title = { Text("Удалить книгу?") },
            text = { Text("«${b.title}» будет удалена из списка. Действие необратимо.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBook(b)
                    deleteBook = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteBook = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun BookRow(
    book: BookEntity,
    canEdit: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.MenuBook, contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(book.author, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!book.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(book.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2)
                }
            }
            if (canEdit) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Действия")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Редактировать") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}
