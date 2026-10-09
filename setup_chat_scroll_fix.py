#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Чат открывается на последнем сообщении, а не первом.
- DMChatScreen, CustomGroupChatScreen: instant scroll при первом показе.
- CommentsSection (чат клуба): добавлен scroll state + scrollToItem.
"""

import shutil
import sys
from pathlib import Path


def find_project_root() -> Path:
    here = Path.cwd()
    for p in [here] + list(here.parents):
        if (p / "settings.gradle.kts").exists() or (p / "settings.gradle").exists():
            return p
    print("❌ Не найден корень проекта.")
    sys.exit(1)


ROOT = find_project_root()
PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "pathfinder" / "hub"


# ============================================================
# 1. DMChatScreen.kt
# ============================================================
DM_SCREEN = '''package com.pathfinder.hub.ui.chat.dm

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.common.AttachmentPreview
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DMChatScreen(
    onBack: () -> Unit,
    viewModel: DMChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var firstScrollDone by remember { mutableStateOf(false) }

    // При первом показе — instant scroll (не мигаем первой страницей).
    // При новых сообщениях — animate.
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
                        text = state.otherName,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                ) {
                    Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(
                            msg = msg,
                            isOwn = msg.authorId == state.currentUserId,
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить",
                            tint = PathfinderBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    msg: com.pathfinder.hub.domain.model.ChatMessage,
    isOwn: Boolean,
) {
    val dateFormat = SimpleDateFormat("HH:mm, d MMM", Locale("ru"))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isOwn) PathfinderBlue.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                msg.attachments.forEach { url ->
                    Spacer(Modifier.height(6.dp))
                    AttachmentPreview(url = url)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(msg.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
'''


# ============================================================
# 2. CustomGroupChatScreen.kt
# ============================================================
GROUP_SCREEN = '''package com.pathfinder.hub.ui.chat.group

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
'''


# ============================================================
# 3. CommentsSection.kt
# ============================================================
COMMENTS_SECTION = '''package com.pathfinder.hub.ui.comments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.common.AttachmentPreview
import com.pathfinder.hub.ui.storage.FileUploadResult
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CommentsSection(
    targetType: String,
    targetId: String,
    clubId: String,
    modifier: Modifier = Modifier,
    pendingAttachment: FileUploadResult? = null,
    onAttachmentConsumed: () -> Unit = {},
    viewModel: CommentsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var firstScrollDone by remember { mutableStateOf(false) }

    LaunchedEffect(targetType, targetId, clubId) {
        viewModel.init(targetType, targetId, clubId)
    }

    LaunchedEffect(pendingAttachment) {
        pendingAttachment?.let { result ->
            viewModel.attachFile(result)
            onAttachmentConsumed()
        }
    }

    // Скролл к последнему сообщению
    LaunchedEffect(state.comments.size, state.isLoading) {
        if (!state.isLoading && state.comments.isNotEmpty()) {
            val target = state.comments.size - 1
            if (!firstScrollDone) {
                listState.scrollToItem(target)
                firstScrollDone = true
            } else {
                listState.animateScrollToItem(target)
            }
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Обсуждение (${state.comments.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (state.comments.isEmpty()) {
            Text(
                text = "Пока нет комментариев. Будьте первым!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.comments, key = { it.id }) { comment ->
                    CommentBubble(comment)
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::onInputChange,
                placeholder = { Text("Написать комментарий...") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                enabled = !state.isSending
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = viewModel::sendComment,
                enabled = state.inputText.isNotBlank() && !state.isSending
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = PathfinderBlue)
                }
            }
        }
    }
}

@Composable
private fun CommentBubble(comment: CommentUiModel) {
    val dateFormat = SimpleDateFormat("HH:mm, d MMM", Locale("ru"))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(36.dp).clip(CircleShape),
            color = if (comment.isOwn) PathfinderBlue else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = comment.authorInitials,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (comment.isOwn)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = dateFormat.format(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (comment.isOwn)
                    PathfinderBlue.copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = comment.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    comment.attachments.forEach { url ->
                        Spacer(Modifier.height(8.dp))
                        AttachmentPreview(url = url)
                    }
                }
            }
        }
    }
}
'''


def write_file(rel: str, content: str) -> bool:
    f = PKG / rel
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False
    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(content, encoding="utf-8")
    print(f"✅ {rel} перезаписан (.bak сохранён)")
    return True


def main():
    print("=" * 60)
    print("  Fix: чат открывается на последнем сообщении")
    print("=" * 60)

    results = {
        "DMChatScreen.kt":         write_file("ui/chat/dm/DMChatScreen.kt", DM_SCREEN),
        "CustomGroupChatScreen.kt": write_file("ui/chat/group/CustomGroupChatScreen.kt", GROUP_SCREEN),
        "CommentsSection.kt":      write_file("ui/comments/CommentsSection.kt", COMMENTS_SECTION),
    }

    print("\n" + "=" * 60)
    for k, v in results.items():
        print(f"  {'✅' if v else '❌'} {k}")

    if all(results.values()):
        print("\n✅ Готово.")
        print("\nСборка: .\\gradlew assembleDebug")
        print("\nТест:")
        print("  1. Открой DM с длинной историей — сразу последнее сообщение")
        print("  2. Выйди, вернись — снова на последнем (без анимации)")
        print("  3. Отправь сообщение — плавно доскроллит до него")
        print("  4. То же для группы и чата клуба")
    else:
        print("\n⚠️  Смотри .bak.")
    print("=" * 60)


if __name__ == "__main__":
    main()