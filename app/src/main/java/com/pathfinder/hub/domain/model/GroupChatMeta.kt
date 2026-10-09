package com.pathfinder.hub.domain.model

import java.util.Date

/**
 * Метаданные группового чата.
 * Firestore: clubs/{clubId}/groupChats/{groupId}
 */
data class GroupChatMeta(
    val id: String,
    val clubId: String,
    val name: String,
    val participants: List<String>,
    val createdBy: String,
    val createdAt: Date,
    val lastMessageAt: Date?,
    val lastMessageText: String?,
)
