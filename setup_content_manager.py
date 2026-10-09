#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub — Сессия 2A. Личные чаты (DM).
Создаёт 7 файлов + патчит 4.
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
if not PKG.exists():
    print(f"❌ Не найден пакет: {PKG}")
    sys.exit(1)


FILES = {}

# =========================================================================
# 1. domain/model/DirectMessageMeta.kt
# =========================================================================
FILES["domain/model/DirectMessageMeta.kt"] = '''package com.pathfinder.hub.domain.model

import java.util.Date

/**
 * Метаданные личного чата (DM).
 * Firestore: clubs/{clubId}/directMessages/{dmId}
 * dmId = sorted([uidA, uidB]).join("_")
 */
data class DirectMessageMeta(
    val id: String,
    val clubId: String,
    val participants: List<String>,
    val createdBy: String,
    val createdAt: Date,
    val lastMessageAt: Date?,
    val lastMessageText: String?,
)

data class ChatMessage(
    val id: String,
    val authorId: String,
    val text: String,
    val attachments: List<String>,
    val createdAt: Date,
    val status: String,
)
'''

# =========================================================================
# 2. data/remote/FirestoreChatDataSource.kt
# =========================================================================
FILES["data/remote/FirestoreChatDataSource.kt"] = '''package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pathfinder.hub.domain.model.ChatMessage
import com.pathfinder.hub.domain.model.DirectMessageMeta
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для личных чатов (DM).
 *
 * Пути:
 *   clubs/{clubId}/directMessages/{dmId}
 *   clubs/{clubId}/directMessages/{dmId}/messages/{msgId}
 *
 * dmId = sorted([uidA, uidB]).join("_")
 */
@Singleton
class FirestoreChatDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreChatDS"
        const val COLLECTION_DMS = "directMessages"
        const val COLLECTION_MESSAGES = "messages"

        fun dmId(a: String, b: String): String =
            listOf(a, b).sorted().joinToString("_")
    }

    private fun dmsRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COLLECTION_DMS)

    private fun messagesRef(clubId: String, dmId: String) =
        dmsRef(clubId).document(dmId).collection(COLLECTION_MESSAGES)

    // ==================== DM (список) ====================

    /**
     * Все DM пользователя в клубе, отсортированные по lastMessageAt desc.
     * Firestore: whereArrayContains("participants", uid)
     */
    fun observeMyDMs(clubId: String, userId: String): Flow<List<DirectMessageMeta>> = callbackFlow {
        if (clubId.isBlank() || userId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = dmsRef(clubId)
            .whereArrayContains("participants", userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "myDMs listener failed", err)
                    trySend(emptyList())
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try { mapDm(doc.id, doc.data ?: emptyMap()) }
                    catch (e: Exception) {
                        Log.e(TAG, "dm map failed ${doc.id}", e); null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeMyDMs error", e)
        emit(emptyList())
    }

    // ==================== DM (сообщения) ====================

    fun observeMessages(clubId: String, dmId: String): Flow<List<ChatMessage>> = callbackFlow {
        if (clubId.isBlank() || dmId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = messagesRef(clubId, dmId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "messages listener failed", err)
                    trySend(emptyList())
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try { mapMessage(doc.id, doc.data ?: emptyMap()) }
                    catch (e: Exception) {
                        Log.e(TAG, "msg map failed ${doc.id}", e); null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeMessages error", e)
        emit(emptyList())
    }

    // ==================== Создание / получение DM ====================

    /**
     * Если DM уже существует — вернёт его id. Иначе создаст пустой документ.
     */
    suspend fun getOrCreateDM(
        clubId: String,
        currentUserId: String,
        otherUserId: String,
    ): Result<String> = try {
        val id = dmId(currentUserId, otherUserId)
        val ref = dmsRef(clubId).document(id)
        val snap = ref.get().await()
        if (!snap.exists()) {
            val now = Date()
            val data = mapOf(
                "id" to id,
                "clubId" to clubId,
                "participants" to listOf(currentUserId, otherUserId),
                "createdBy" to currentUserId,
                "createdAt" to Timestamp(now),
                "lastMessageAt" to Timestamp(now),
                "lastMessageText" to "",
            )
            ref.set(data).await()
            Log.d(TAG, "DM created: $id")
        }
        Result.success(id)
    } catch (e: Exception) {
        Log.e(TAG, "getOrCreateDM failed", e)
        Result.failure(e)
    }

    // ==================== Отправка ====================

    suspend fun sendMessage(
        clubId: String,
        dmId: String,
        authorId: String,
        text: String,
        attachments: List<String>,
    ): Result<Unit> = try {
        val now = Date()
        val msgId = java.util.UUID.randomUUID().toString()
        val data = mapOf(
            "id" to msgId,
            "authorId" to authorId,
            "text" to text,
            "attachments" to attachments,
            "createdAt" to Timestamp(now),
            "status" to "published",
        )
        messagesRef(clubId, dmId).document(msgId).set(data).await()

        // Обновляем метаданные DM для списка
        dmsRef(clubId).document(dmId).update(
            mapOf(
                "lastMessageAt" to Timestamp(now),
                "lastMessageText" to text,
            )
        ).await()

        Log.d(TAG, "DM msg sent: $dmId/$msgId")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "sendMessage failed", e)
        Result.failure(e)
    }

    // ==================== Маппинг ====================

    private fun mapDm(id: String, d: Map<String, Any?>): DirectMessageMeta =
        DirectMessageMeta(
            id = id,
            clubId = d["clubId"] as? String ?: "",
            participants = (d["participants"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            createdBy = d["createdBy"] as? String ?: "",
            createdAt = parseDate(d["createdAt"]) ?: Date(),
            lastMessageAt = parseDate(d["lastMessageAt"]),
            lastMessageText = d["lastMessageText"] as? String,
        )

    private fun mapMessage(id: String, d: Map<String, Any?>): ChatMessage =
        ChatMessage(
            id = id,
            authorId = d["authorId"] as? String ?: "",
            text = d["text"] as? String ?: "",
            attachments = (d["attachments"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            createdAt = parseDate(d["createdAt"]) ?: Date(),
            status = d["status"] as? String ?: "published",
        )

    private fun parseDate(v: Any?): Date? = when (v) {
        null -> null
        is Date -> v
        is Timestamp -> v.toDate()
        is Long -> Date(v)
        else -> null
    }
}
'''

# =========================================================================
# 3. data/repository/ChatRepository.kt
# =========================================================================
FILES["data/repository/ChatRepository.kt"] = '''package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.remote.FirestoreChatDataSource
import com.pathfinder.hub.domain.model.ChatMessage
import com.pathfinder.hub.domain.model.DirectMessageMeta
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirestoreChatDataSource,
) {

    // ==================== Чтение ====================

    fun observeMyDMs(clubId: String, userId: String): Flow<List<DirectMessageMeta>> =
        firestore.observeMyDMs(clubId, userId)

    fun observeMessages(clubId: String, dmId: String): Flow<List<ChatMessage>> =
        firestore.observeMessages(clubId, dmId)

    // ==================== Действия ====================

    /**
     * Открыть или создать DM с пользователем.
     * Возвращает dmId, который используется в навигации.
     */
    suspend fun openDM(
        clubId: String,
        currentUserId: String,
        otherUserId: String,
    ): Result<String> {
        if (currentUserId == otherUserId) {
            return Result.failure(IllegalArgumentException("Нельзя открыть DM с самим собой"))
        }
        return firestore.getOrCreateDM(clubId, currentUserId, otherUserId)
    }

    suspend fun sendMessage(
        clubId: String,
        dmId: String,
        authorId: String,
        text: String,
        attachments: List<String> = emptyList(),
    ): Result<Unit> {
        if (text.isBlank() && attachments.isEmpty()) {
            return Result.failure(IllegalArgumentException("Пустое сообщение"))
        }
        return firestore.sendMessage(clubId, dmId, authorId, text, attachments)
    }

    // ==================== Утилиты ====================

    fun dmId(a: String, b: String): String = FirestoreChatDataSource.dmId(a, b)

    /** Возвращает id собеседника (не равный userId). */
    fun otherParticipant(dm: DirectMessageMeta, userId: String): String =
        dm.participants.firstOrNull { it != userId } ?: ""
}
'''

# =========================================================================
# 4. ui/chat/list/ChatsListViewModel.kt
# =========================================================================
FILES["ui/chat/list/ChatsListViewModel.kt"] = '''package com.pathfinder.hub.ui.chat.list

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.ChatRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.model.DirectMessageMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DmListItem(
    val dm: DirectMessageMeta,
    val otherUserId: String,
    val otherName: String,
    val otherInitials: String,
)

data class ChatsListUiState(
    val isLoading: Boolean = true,
    val clubId: String? = null,
    val currentUserId: String? = null,
    val dms: List<DmListItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ChatsListViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val chatRepo: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatsListUiState())
    val state: StateFlow<ChatsListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = sessionManager.getUserId()
            if (uid.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
                return@launch
            }
            val user = userRepository.getUser(uid)
            val clubId = user?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            _state.update { it.copy(clubId = clubId, currentUserId = uid) }

            chatRepo.observeMyDMs(clubId, uid)
                .catch { e ->
                    Log.e(TAG, "observeMyDMs error", e)
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
                .collect { list ->
                    val enriched = list.map { dm ->
                        val other = chatRepo.otherParticipant(dm, uid)
                        val u = if (other.isNotBlank()) userRepository.getUser(other) else null
                        val initials = u?.let {
                            "${it.firstName.firstOrNull()?.uppercase() ?: ""}${it.lastName.firstOrNull()?.uppercase() ?: ""}"
                        } ?: "?"
                        DmListItem(
                            dm = dm,
                            otherUserId = other,
                            otherName = u?.let { "${it.firstName} ${it.lastName}" } ?: "Собеседник",
                            otherInitials = initials,
                        )
                    }.sortedByDescending { it.dm.lastMessageAt ?: it.dm.createdAt }

                    _state.update { it.copy(isLoading = false, dms = enriched) }
                }
        }
    }

    companion object { private const val TAG = "ChatsListVM" }
}
'''

# =========================================================================
# 5. ui/chat/list/ChatsListScreen.kt
# =========================================================================
FILES["ui/chat/list/ChatsListScreen.kt"] = '''package com.pathfinder.hub.ui.chat.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    onBack: () -> Unit,
    onOpenClubChat: (clubId: String) -> Unit,
    onOpenDM: (dmId: String, otherUid: String) -> Unit,
    viewModel: ChatsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чаты") },
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
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.errorMessage != null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ---------- Чат клуба ----------
                item {
                    Text(
                        text = "Общие",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Card(
                        onClick = { state.clubId?.let(onOpenClubChat) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null,
                                tint = PathfinderBlue, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Чат клуба",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("Все участники клуба",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Chat, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ---------- Личные ----------
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Личные (${state.dms.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.dms.isEmpty()) {
                    item {
                        Text(
                            "Пока нет личных чатов",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.dms, key = { it.dm.id }) { item ->
                        Card(
                            onClick = { onOpenDM(item.dm.id, item.otherUserId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp).clip(CircleShape),
                                    color = PathfinderBlue.copy(alpha = 0.2f),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = item.otherInitials,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PathfinderBlue,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.otherName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    val preview = item.dm.lastMessageText
                                    if (!preview.isNullOrBlank()) {
                                        Text(
                                            text = preview.take(60),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Icon(Icons.Default.Person, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
'''

# =========================================================================
# 6. ui/chat/dm/DMChatViewModel.kt
# =========================================================================
FILES["ui/chat/dm/DMChatViewModel.kt"] = '''package com.pathfinder.hub.ui.chat.dm

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.ChatRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DMChatUiState(
    val isLoading: Boolean = true,
    val clubId: String? = null,
    val dmId: String? = null,
    val currentUserId: String? = null,
    val otherUserId: String = "",
    val otherName: String = "Собеседник",
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class DMChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val chatRepo: ChatRepository,
) : ViewModel() {

    private val dmId: String = savedStateHandle.get<String>("dmId") ?: ""
    private val otherUid: String = savedStateHandle.get<String>("otherUid") ?: ""

    private val _state = MutableStateFlow(DMChatUiState())
    val state: StateFlow<DMChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = sessionManager.getUserId()
            if (uid.isNullOrBlank() || dmId.isBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Нет данных чата") }
                return@launch
            }
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }

            val other = if (otherUid.isNotBlank()) userRepository.getUser(otherUid) else null
            val otherName = other?.let { "${it.firstName} ${it.lastName}" } ?: "Собеседник"

            _state.update {
                it.copy(
                    clubId = clubId,
                    dmId = dmId,
                    currentUserId = uid,
                    otherUserId = otherUid,
                    otherName = otherName,
                )
            }

            chatRepo.observeMessages(clubId, dmId)
                .catch { e ->
                    Log.e(TAG, "observeMessages error", e)
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
                .collect { list ->
                    _state.update { it.copy(messages = list, isLoading = false) }
                }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun send() {
        val s = _state.value
        val text = s.inputText.trim()
        if (text.isBlank()) return
        val clubId = s.clubId ?: return
        val dm = s.dmId ?: return
        val uid = s.currentUserId ?: return

        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            val r = chatRepo.sendMessage(clubId, dm, uid, text)
            r.fold(
                onSuccess = { _state.update { it.copy(inputText = "", isSending = false) } },
                onFailure = { e ->
                    _state.update { it.copy(isSending = false, errorMessage = e.message) }
                },
            )
        }
    }

    companion object { private const val TAG = "DMChatVM" }
}
'''

# =========================================================================
# 7. ui/chat/dm/DMChatScreen.kt
# =========================================================================
FILES["ui/chat/dm/DMChatScreen.kt"] = '''package com.pathfinder.hub.ui.chat.dm

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

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.otherName) },
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
# ЗАПИСЬ
# ============================================================
def write_files() -> None:
    print(f"📁 Пакет: {PKG}\n")
    for rel, content in FILES.items():
        target = PKG / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            shutil.copy2(target, target.with_suffix(target.suffix + ".bak"))
            print(f"⚠️  Перезаписываю {rel}")
        target.write_text(content, encoding="utf-8")
        print(f"✅ {rel}")


# ============================================================
# ПАТЧ 1 — Routes.kt
# ============================================================
def patch_routes() -> bool:
    print("\n🔧 Патчу Routes.kt...")
    f = PKG / "ui/navigation/Routes.kt"
    text = f.read_text(encoding="utf-8")
    if "CHATS_LIST" in text:
        print("⚠️  Уже пропатчен")
        return True

    anchor = '    const val CLUB_CHAT = "club_chat"'
    if anchor not in text:
        print("❌ Нет якоря CLUB_CHAT")
        return False

    new = '''    const val CLUB_CHAT = "club_chat"
    const val CHATS_LIST = "chats_list"
    const val DM_CHAT = "dm_chat/{dmId}/{otherUid}"
    fun dmChat(dmId: String, otherUid: String) = "dm_chat/$dmId/$otherUid"'''
    text = text.replace(anchor, new, 1)
    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print("✅ Routes.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 2 — PathfinderNavHost
# ============================================================
def patch_navhost() -> bool:
    print("\n🔧 Патчу PathfinderNavHost.kt...")
    f = PKG / "ui/navigation/PathfinderNavHost.kt"
    text = f.read_text(encoding="utf-8")
    if "ChatsListScreen" in text:
        print("⚠️  Уже пропатчен")
        return True

    # Импорты
    imp_anchor = "import com.pathfinder.hub.ui.chat.ClubChatScreen"
    if imp_anchor not in text:
        print("❌ Нет якоря импорта ClubChatScreen")
        return False
    text = text.replace(
        imp_anchor,
        "import com.pathfinder.hub.ui.chat.list.ChatsListScreen\n"
        + "import com.pathfinder.hub.ui.chat.dm.DMChatScreen\n"
        + imp_anchor,
        1,
    )

    # Новые composable — перед "// === Teen - Honors ==="
    anchor = "        // === Teen - Honors ==="
    if anchor not in text:
        print("❌ Не найден якорь Teen - Honors")
        return False

    block = '''        // === Chats List + DM ===
        composable(Routes.CHATS_LIST) {
            ChatsListScreen(
                onBack = { navController.popBackStack() },
                onOpenClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onOpenDM = { dmId, otherUid ->
                    navController.navigate(Routes.dmChat(dmId, otherUid))
                },
            )
        }

        composable(
            route = Routes.DM_CHAT,
            arguments = listOf(
                navArgument("dmId") { type = NavType.StringType },
                navArgument("otherUid") { type = NavType.StringType },
            ),
        ) {
            DMChatScreen(onBack = { navController.popBackStack() })
        }

'''
    text = text.replace(anchor, block + anchor, 1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print("✅ PathfinderNavHost.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 3 — DirectorHomeScreen
# ============================================================
def patch_director_home() -> bool:
    print("\n🔧 Патчу DirectorHomeScreen.kt...")
    f = PKG / "ui/screens/home/director/DirectorHomeScreen.kt"
    text = f.read_text(encoding="utf-8")
    if "onNavigateToChats" in text:
        print("⚠️  Уже пропатчен")
        return True

    # 1) Параметр в сигнатуру
    sig_anchor = "    onNavigateToContentManager: () -> Unit = {},"
    if sig_anchor not in text:
        print("❌ Нет якоря onNavigateToContentManager")
        return False
    text = text.replace(
        sig_anchor,
        sig_anchor + "\n    onNavigateToChats: () -> Unit = {},",
        1,
    )

    # 2) Заменяем строку "Модерация + Контент" — добавим Чаты
    old_row = '''            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DirectorActionCard(Icons.Default.Shield, "Модерация", "Проверка сообщений", onNavigateToModeration, Modifier.weight(1f))
                DirectorActionCard(Icons.Default.Edit, "Контент", "Ступени и требования", onNavigateToContentManager, Modifier.weight(1f))
            }'''
    new_row = '''            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DirectorActionCard(Icons.Default.Shield, "Модерация", "Проверка сообщений", onNavigateToModeration, Modifier.weight(1f))
                DirectorActionCard(Icons.Default.Edit, "Контент", "Ступени и требования", onNavigateToContentManager, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DirectorActionCard(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }'''
    if old_row not in text:
        print("❌ Не найден блок Модерация+Контент")
        return False
    text = text.replace(old_row, new_row, 1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print("✅ DirectorHomeScreen.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 4 — firestore.rules
# ============================================================
def patch_rules() -> bool:
    print("\n🔧 Патчу firestore.rules...")
    f = ROOT / "firestore.rules"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False
    text = f.read_text(encoding="utf-8")
    if "directMessages" in text:
        print("⚠️  Уже пропатчен")
        return True

    anchor = "      match /requirementTaskAnswers/{answerId} {"
    if anchor not in text:
        print("❌ Нет якоря requirementTaskAnswers")
        return False

    new_section = '''      // ============================================================
      // DIRECT MESSAGES — личные чаты (DM)
      // clubs/{clubId}/directMessages/{dmId}/messages/{msgId}
      // dmId = sorted([uidA, uidB]).join("_")
      // ============================================================
      match /directMessages/{dmId} {
        allow read: if isSignedIn() && (
          request.auth.uid in resource.data.participants
          || isConferenceAdmin()
        );
        allow create: if isSignedIn()
          && isOwnClub(clubId)
          && request.auth.uid in request.resource.data.participants
          && request.resource.data.participants.size() == 2
          && request.resource.data.clubId == clubId;
        allow update: if isSignedIn()
          && request.auth.uid in resource.data.participants;
        allow delete: if isConferenceAdmin();

        match /messages/{msgId} {
          allow read: if isSignedIn()
            && request.auth.uid in get(/databases/$(database)/documents/clubs/$(clubId)/directMessages/$(dmId)).data.participants;
          allow create: if isSignedIn()
            && isOwnClub(clubId)
            && request.resource.data.authorId == uid();
          allow update, delete: if false;
        }
      }

'''
    text = text.replace(anchor, new_section + anchor, 1)
    shutil.copy2(f, f.with_suffix(".bak"))
    f.write_text(text, encoding="utf-8")
    print("✅ firestore.rules пропатчен")
    return True


# ============================================================
# MAIN
# ============================================================
def main():
    print("=" * 60)
    print("  Pathfinder Hub — Сессия 2A: DM Chats")
    print("=" * 60)

    write_files()

    r = {
        "Routes.kt":              patch_routes(),
        "PathfinderNavHost.kt":   patch_navhost(),
        "DirectorHomeScreen.kt":  patch_director_home(),
        "firestore.rules":        patch_rules(),
    }

    print("\n" + "=" * 60)
    for k, v in r.items():
        print(f"  {'✅' if v else '❌'} {k}")

    if all(r.values()):
        print("\n✅ Готово.")
        print("\nСборка: .\\gradlew assembleDebug")
        print("Затем: firebase deploy --only firestore:rules")
        print("\nТест:")
        print("  1. Директор → карточка «Чаты» → список")
        print("  2. Строка «Чат клуба» → существующий ClubChatScreen")
        print("  3. Раздел «Личные» — пусто (пока нет DM)")
        print("  Для открытия DM нужен UI создания — добавим в 2B")
    else:
        print("\n⚠️  Смотри .bak.")

    print("=" * 60)


if __name__ == "__main__":
    main()