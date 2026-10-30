package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.pathfinder.hub.domain.model.ChatMessage
import com.pathfinder.hub.domain.model.DirectMessageMeta
import com.pathfinder.hub.domain.model.GroupChatMeta
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreChatDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreChatDS"
        const val COLLECTION_DMS = "directMessages"
        const val COLLECTION_GROUPS = "groupChats"
        const val COLLECTION_MESSAGES = "messages"

        fun dmId(a: String, b: String): String =
            listOf(a, b).sorted().joinToString("_")
    }

    private fun dmsRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COLLECTION_DMS)

    private fun dmMessagesRef(clubId: String, dmId: String) =
        dmsRef(clubId).document(dmId).collection(COLLECTION_MESSAGES)

    private fun groupsRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COLLECTION_GROUPS)

    private fun groupMessagesRef(clubId: String, groupId: String) =
        groupsRef(clubId).document(groupId).collection(COLLECTION_MESSAGES)

    // ==================== DM: listeners ====================

    fun observeMyDMs(clubId: String, userId: String): Flow<List<DirectMessageMeta>> = callbackFlow {
        if (clubId.isBlank() || userId.isBlank()) {
            trySend(emptyList()); awaitClose { }; return@callbackFlow
        }
        val registration = dmsRef(clubId)
            .whereArrayContains("participants", userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "myDMs listener failed", err)
                    trySend(emptyList()); close(err); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try { mapDm(doc.id, doc.data ?: emptyMap()) }
                    catch (e: Exception) { Log.e(TAG, "dm map ${doc.id}", e); null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e -> Log.e(TAG, "observeMyDMs error", e); emit(emptyList()) }

    fun observeDmMessages(clubId: String, dmId: String): Flow<List<ChatMessage>> = callbackFlow {
        if (clubId.isBlank() || dmId.isBlank()) {
            trySend(emptyList()); awaitClose { }; return@callbackFlow
        }
        val registration = dmMessagesRef(clubId, dmId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "dm messages listener failed", err)
                    trySend(emptyList()); close(err); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try { mapMessage(doc.id, doc.data ?: emptyMap()) }
                    catch (e: Exception) { Log.e(TAG, "msg map ${doc.id}", e); null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e -> Log.e(TAG, "observeDmMessages error", e); emit(emptyList()) }

    // ==================== DM: разовые ====================

    suspend fun getMyDmsOnce(clubId: String, userId: String): List<DirectMessageMeta> {
        return try {
            val snap = dmsRef(clubId)
                .whereArrayContains("participants", userId)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                try { mapDm(doc.id, doc.data ?: emptyMap()) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMyDmsOnce failed", e); emptyList()
        }
    }

    suspend fun getDmMessagesSince(
        clubId: String, dmId: String, sinceMs: Long,
    ): List<ChatMessage> {
        return try {
            val snap = dmMessagesRef(clubId, dmId)
                .whereGreaterThan("createdAt", Timestamp(Date(sinceMs)))
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                try { mapMessage(doc.id, doc.data ?: emptyMap()) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDmMessagesSince failed", e); emptyList()
        }
    }

    // ==================== GROUPS: listeners ====================

    fun observeMyGroups(clubId: String, userId: String): Flow<List<GroupChatMeta>> = callbackFlow {
        if (clubId.isBlank() || userId.isBlank()) {
            trySend(emptyList()); awaitClose { }; return@callbackFlow
        }
        val registration = groupsRef(clubId)
            .whereArrayContains("participants", userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "myGroups listener failed", err)
                    trySend(emptyList()); close(err); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try { mapGroup(doc.id, doc.data ?: emptyMap()) }
                    catch (e: Exception) { Log.e(TAG, "group map ${doc.id}", e); null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e -> Log.e(TAG, "observeMyGroups error", e); emit(emptyList()) }

    fun observeGroupMeta(clubId: String, groupId: String): Flow<GroupChatMeta?> = callbackFlow {
        if (clubId.isBlank() || groupId.isBlank()) {
            trySend(null); awaitClose { }; return@callbackFlow
        }
        val registration = groupsRef(clubId).document(groupId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "group meta listener failed", err)
                    trySend(null); close(err); return@addSnapshotListener
                }
                trySend(snap?.takeIf { it.exists() }?.let { mapGroup(it.id, it.data ?: emptyMap()) })
            }
        awaitClose { registration.remove() }
    }.catch { e -> Log.e(TAG, "observeGroupMeta error", e); emit(null) }

    fun observeGroupMessages(clubId: String, groupId: String): Flow<List<ChatMessage>> = callbackFlow {
        if (clubId.isBlank() || groupId.isBlank()) {
            trySend(emptyList()); awaitClose { }; return@callbackFlow
        }
        val registration = groupMessagesRef(clubId, groupId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "group messages listener failed", err)
                    trySend(emptyList()); close(err); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try { mapMessage(doc.id, doc.data ?: emptyMap()) }
                    catch (e: Exception) { Log.e(TAG, "msg map ${doc.id}", e); null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e -> Log.e(TAG, "observeGroupMessages error", e); emit(emptyList()) }

    // ==================== GROUPS: разовые ====================

    suspend fun getMyGroupsOnce(clubId: String, userId: String): List<GroupChatMeta> {
        return try {
            val snap = groupsRef(clubId)
                .whereArrayContains("participants", userId)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                try { mapGroup(doc.id, doc.data ?: emptyMap()) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMyGroupsOnce failed", e); emptyList()
        }
    }

    suspend fun getGroupMessagesSince(
        clubId: String, groupId: String, sinceMs: Long,
    ): List<ChatMessage> {
        return try {
            val snap = groupMessagesRef(clubId, groupId)
                .whereGreaterThan("createdAt", Timestamp(Date(sinceMs)))
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                try { mapMessage(doc.id, doc.data ?: emptyMap()) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getGroupMessagesSince failed", e); emptyList()
        }
    }

    // ==================== Создание DM / группы ====================

    suspend fun getOrCreateDM(
        clubId: String, currentUserId: String, otherUserId: String,
    ): Result<String> {
        return try {
            val id = dmId(currentUserId, otherUserId)
            val ref = dmsRef(clubId).document(id)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val now = Date()
                ref.set(
                    mapOf(
                        "id" to id,
                        "clubId" to clubId,
                        "participants" to listOf(currentUserId, otherUserId),
                        "createdBy" to currentUserId,
                        "createdAt" to Timestamp(now),
                        "lastMessageAt" to Timestamp(now),
                        "lastMessageText" to "",
                    )
                ).await()
            }
            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "getOrCreateDM failed", e); Result.failure(e)
        }
    }

    suspend fun sendDmMessage(
        clubId: String, dmId: String, authorId: String,
        text: String, attachments: List<String>,
    ): Result<Unit> {
        return try {
            val now = Date()
            val msgId = UUID.randomUUID().toString()
            dmMessagesRef(clubId, dmId).document(msgId).set(
                mapOf(
                    "id" to msgId, "authorId" to authorId, "text" to text,
                    "attachments" to attachments,
                    "createdAt" to Timestamp(now), "status" to "published",
                )
            ).await()
            dmsRef(clubId).document(dmId).update(
                mapOf("lastMessageAt" to Timestamp(now), "lastMessageText" to text)
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendDmMessage failed", e); Result.failure(e)
        }
    }

    suspend fun createGroup(
        clubId: String, creatorId: String, name: String, participants: List<String>,
    ): Result<String> {
        return try {
            val groupId = UUID.randomUUID().toString()
            val all = (participants + creatorId).distinct()
            val now = Date()
            groupsRef(clubId).document(groupId).set(
                mapOf(
                    "id" to groupId,
                    "clubId" to clubId,
                    "name" to name,
                    "participants" to all,
                    "createdBy" to creatorId,
                    "createdAt" to Timestamp(now),
                    "lastMessageAt" to Timestamp(now),
                    "lastMessageText" to "",
                )
            ).await()
            Log.d(TAG, "group created: $groupId")
            Result.success(groupId)
        } catch (e: Exception) {
            Log.e(TAG, "createGroup failed", e); Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(
        clubId: String, groupId: String, authorId: String,
        text: String, attachments: List<String>,
    ): Result<Unit> {
        return try {
            val now = Date()
            val msgId = UUID.randomUUID().toString()
            groupMessagesRef(clubId, groupId).document(msgId).set(
                mapOf(
                    "id" to msgId, "authorId" to authorId, "text" to text,
                    "attachments" to attachments,
                    "createdAt" to Timestamp(now), "status" to "published",
                )
            ).await()
            groupsRef(clubId).document(groupId).update(
                mapOf("lastMessageAt" to Timestamp(now), "lastMessageText" to text)
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendGroupMessage failed", e); Result.failure(e)
        }
    }

    // ==================== Редактирование ====================

    suspend fun editDmMessage(
        clubId: String, dmId: String, messageId: String,
        authorId: String, newText: String,
    ): Result<Unit> = try {
        if (newText.isBlank()) {
            throw IllegalArgumentException("Текст не может быть пустым")
        }
        val ref = dmMessagesRef(clubId, dmId).document(messageId)
        val snap = ref.get().await()
        if (!snap.exists()) throw IllegalStateException("Сообщение не найдено")
        val realAuthor = snap.getString("authorId") ?: ""
        if (realAuthor != authorId) throw IllegalStateException("Можно редактировать только свои сообщения")
        ref.update(
            mapOf(
                "text" to newText,
                "editedAt" to Timestamp(Date()),
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "editDmMessage failed", e)
        Result.failure(e)
    }

    suspend fun editGroupMessage(
        clubId: String, groupId: String, messageId: String,
        authorId: String, newText: String,
    ): Result<Unit> = try {
        if (newText.isBlank()) {
            throw IllegalArgumentException("Текст не может быть пустым")
        }
        val ref = groupMessagesRef(clubId, groupId).document(messageId)
        val snap = ref.get().await()
        if (!snap.exists()) throw IllegalStateException("Сообщение не найдено")
        val realAuthor = snap.getString("authorId") ?: ""
        if (realAuthor != authorId) throw IllegalStateException("Можно редактировать только свои сообщения")
        ref.update(
            mapOf(
                "text" to newText,
                "editedAt" to Timestamp(Date()),
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "editGroupMessage failed", e)
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

    private fun mapGroup(id: String, d: Map<String, Any?>): GroupChatMeta =
        GroupChatMeta(
            id = id,
            clubId = d["clubId"] as? String ?: "",
            name = d["name"] as? String ?: "Группа",
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
            editedAt = parseDate(d["editedAt"]),
        )

    private fun parseDate(v: Any?): Date? = when (v) {
        null -> null
        is Date -> v
        is Timestamp -> v.toDate()
        is Long -> Date(v)
        else -> null
    }
}