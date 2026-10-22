package com.pathfinder.hub.domain.model

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
    val editedAt: Date? = null,
)
