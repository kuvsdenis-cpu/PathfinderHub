package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.remote.FirestoreChatDataSource
import com.pathfinder.hub.domain.model.ChatMessage
import com.pathfinder.hub.domain.model.DirectMessageMeta
import com.pathfinder.hub.domain.model.GroupChatMeta
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirestoreChatDataSource,
) {

    // ==================== DM ====================

    fun observeMyDMs(clubId: String, userId: String): Flow<List<DirectMessageMeta>> =
        firestore.observeMyDMs(clubId, userId)

    fun observeDmMessages(clubId: String, dmId: String): Flow<List<ChatMessage>> =
        firestore.observeDmMessages(clubId, dmId)

    suspend fun openDM(
        clubId: String, currentUserId: String, otherUserId: String,
    ): Result<String> {
        if (currentUserId == otherUserId) {
            return Result.failure(IllegalArgumentException("Нельзя открыть DM с самим собой"))
        }
        return firestore.getOrCreateDM(clubId, currentUserId, otherUserId)
    }

    suspend fun sendDmMessage(
        clubId: String, dmId: String, authorId: String,
        text: String, attachments: List<String> = emptyList(),
    ): Result<Unit> {
        if (text.isBlank() && attachments.isEmpty()) {
            return Result.failure(IllegalArgumentException("Пустое сообщение"))
        }
        return firestore.sendDmMessage(clubId, dmId, authorId, text, attachments)
    }

    // ==================== GROUPS ====================

    fun observeMyGroups(clubId: String, userId: String): Flow<List<GroupChatMeta>> =
        firestore.observeMyGroups(clubId, userId)

    fun observeGroupMeta(clubId: String, groupId: String): Flow<GroupChatMeta?> =
        firestore.observeGroupMeta(clubId, groupId)

    fun observeGroupMessages(clubId: String, groupId: String): Flow<List<ChatMessage>> =
        firestore.observeGroupMessages(clubId, groupId)

    suspend fun createGroup(
        clubId: String, creatorId: String, name: String, participants: List<String>,
    ): Result<String> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Введите название"))
        if (participants.isEmpty()) return Result.failure(IllegalArgumentException("Выберите участников"))
        return firestore.createGroup(clubId, creatorId, name.trim(), participants)
    }

    suspend fun sendGroupMessage(
        clubId: String, groupId: String, authorId: String,
        text: String, attachments: List<String> = emptyList(),
    ): Result<Unit> {
        if (text.isBlank() && attachments.isEmpty()) {
            return Result.failure(IllegalArgumentException("Пустое сообщение"))
        }
        return firestore.sendGroupMessage(clubId, groupId, authorId, text, attachments)
    }

    suspend fun editDmMessage(
        clubId: String, dmId: String, messageId: String,
        authorId: String, newText: String,
    ): Result<Unit> {
        if (newText.isBlank()) return Result.failure(IllegalArgumentException("Пустое сообщение"))
        return firestore.editDmMessage(clubId, dmId, messageId, authorId, newText.trim())
    }

    suspend fun editGroupMessage(
        clubId: String, groupId: String, messageId: String,
        authorId: String, newText: String,
    ): Result<Unit> {
        if (newText.isBlank()) return Result.failure(IllegalArgumentException("Пустое сообщение"))
        return firestore.editGroupMessage(clubId, groupId, messageId, authorId, newText.trim())
    }

    // ==================== Разовые запросы ====================

    suspend fun getMyDmsOnce(clubId: String, userId: String): List<DirectMessageMeta> =
        firestore.getMyDmsOnce(clubId, userId)

    suspend fun getMyGroupsOnce(clubId: String, userId: String): List<GroupChatMeta> =
        firestore.getMyGroupsOnce(clubId, userId)

    suspend fun getDmMessagesSince(
        clubId: String, dmId: String, sinceMs: Long,
    ): List<ChatMessage> = firestore.getDmMessagesSince(clubId, dmId, sinceMs)

    suspend fun getGroupMessagesSince(
        clubId: String, groupId: String, sinceMs: Long,
    ): List<ChatMessage> = firestore.getGroupMessagesSince(clubId, groupId, sinceMs)

    // ==================== Summary при логине ====================

    /**
     * Считает сколько DM/групп имеют последнее сообщение позже sinceMs.
     * Используется для summary-уведомления при холодном старте.
     */
    suspend fun countChatsWithNewMessages(
        clubId: String, userId: String, sinceMs: Long,
    ): Int {
        val dms = try {
            firestore.getMyDmsOnce(clubId, userId)
        } catch (_: Exception) {
            emptyList()
        }
        val groups = try {
            firestore.getMyGroupsOnce(clubId, userId)
        } catch (_: Exception) {
            emptyList()
        }
        val dmCount = dms.count { (it.lastMessageAt?.time ?: 0L) > sinceMs }
        val grCount = groups.count { (it.lastMessageAt?.time ?: 0L) > sinceMs }
        return dmCount + grCount
    }

    // ==================== Утилиты ====================

    fun dmId(a: String, b: String): String = FirestoreChatDataSource.dmId(a, b)

    fun otherParticipant(dm: DirectMessageMeta, userId: String): String =
        dm.participants.firstOrNull { it != userId } ?: ""
}