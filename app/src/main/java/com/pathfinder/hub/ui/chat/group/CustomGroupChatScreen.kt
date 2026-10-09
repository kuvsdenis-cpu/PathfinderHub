package com.pathfinder.hub.ui.chat.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.common.AttachmentPreview
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomGroupChatScreen(
    onBack: () -> Unit,
    viewModel: CustomGroupChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var firstScrollDone by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size, state.isLoading) {
        if (!state.isLoading && state.messages.isNotEmpty()) {
            val target = state.messages.size - 1
            if (!firstScrollDone) {
                listState.scrollToItem(target)
                firstScrollDone = true
            } else {
                listState.animateScrollToItem(target)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.groupName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(Modifier.weight(1f), Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null && state.messages.isEmpty() -> Box(
                    Modifier.weight(1f), Alignment.Center,
                ) { Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error) }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        GroupMessageBubble(msg = msg)
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = viewModel::onInputChange,
                    placeholder = { Text("Сообщение...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    enabled = !state.isSending,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = viewModel::send,
                    enabled = state.inputText.isNotBlank() && !state.isSending,
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = PathfinderBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(msg: GroupMessageUi) {
    val fmt = SimpleDateFormat("HH:mm, d MMM", Locale("ru"))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isOwn) Arrangement.End else Arrangement.Start,
    ) {
        if (!msg.isOwn) {
            Surface(
                modifier = Modifier.size(32.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(msg.authorInitials,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (msg.isOwn) PathfinderBlue.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (!msg.isOwn) {
                    Text(msg.authorName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PathfinderBlue)
                    Spacer(Modifier.height(2.dp))
                }
                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                msg.attachments.forEach { url ->
                    Spacer(Modifier.height(6.dp))
                    AttachmentPreview(url = url)
                }
                Spacer(Modifier.height(4.dp))
                Text(fmt.format(msg.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
