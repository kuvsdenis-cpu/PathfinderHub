package com.pathfinder.hub.data.local.entity.auth
import androidx.room.*
import java.util.Date

@Entity(
    tableName = "parent_child_links",
    primaryKeys = ["parentId", "childId"],
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["childId"]),
        Index(value = ["clubId"]),
    ]
)
data class ParentChildLinkEntity(
    val parentId: String, val childId: String, val clubId: String,
    val relationship: String, val status: String, val linkedBy: String,
    val linkedAt: Date, val revokedAt: Date?, val revokedReason: String?,
    // Вариант A: код инвайта, по которому создана связь (для firestore.rules)
    val inviteCode: String? = null
)
