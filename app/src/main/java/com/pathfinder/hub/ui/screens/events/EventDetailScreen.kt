package com.pathfinder.hub.ui.screens.events

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.pathfinder.hub.ui.share.ShareCardGenerator
import com.pathfinder.hub.ui.share.ShareCardHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Событие") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    val ctx = LocalContext.current
                    val scope = rememberCoroutineScope()
                    val event = state.event
                    if (event != null) {
                        IconButton(onClick = {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    ShareCardGenerator.generate(
                                        context = ctx,
                                        emoji = "\uD83D\uDCC5",
                                        title = event.title,
                                        subtitle = event.location.ifBlank { "Событие клуба" },
                                        userName = null,
                                        dateText = fmt.format(event.dateStart),
                                    )
                                }
                                ShareCardHelper.share(ctx, file, "Событие в Pathfinder Hub: " + event.title)
                            }
                        }) {
                            Icon(Icons.Default.Share, "Поделиться")
                        }
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
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        val e = state.event ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(e.title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text("Дата: ${fmt.format(e.dateStart)}",
                style = MaterialTheme.typography.bodyLarge)
            if (e.location.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Место: ${e.location}",
                    style = MaterialTheme.typography.bodyLarge)
            }
            if (e.description.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(e.description, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}