package com.pathfinder.hub.ui.screens.home.director.sections

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.learning.BookReportEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffBooksContent(
    modifier: Modifier = Modifier,
    viewModel: StaffBooksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = state.selectedTab) {
                    Tab(
                        selected = state.selectedTab == 0,
                        onClick = { viewModel.setTab(0) },
                        text = { Text("На проверке (${state.pending.size})") },
                    )
                    Tab(
                        selected = state.selectedTab == 1,
                        onClick = { viewModel.setTab(1) },
                        text = { Text("Проверено (${state.reviewed.size})") },
                    )
                }
                val items = (if (state.selectedTab == 0) state.pending else state.reviewed)
                    .filter { it.type == "summary" }
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Book, null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (state.selectedTab == 0) "Нет отчётов на проверке"
                                else "Проверенных отчётов пока нет",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items, key = { it.id }) { r ->
                            BookCard(
                                r = r,
                                childName = state.childNames[r.userId] ?: r.userId,
                                showActions = state.selectedTab == 0,
                                onApprove = { viewModel.approve(r) },
                                onReject = { viewModel.reject(r) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCard(
    r: BookReportEntity,
    childName: String,
    showActions: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                childName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Книга: ${r.bookId.take(8)} · Тип: ${r.type}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                r.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
            )
            if (showActions) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onReject) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Вернуть")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onApprove) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Одобрить")
                    }
                }
            }
        }
    }
}
