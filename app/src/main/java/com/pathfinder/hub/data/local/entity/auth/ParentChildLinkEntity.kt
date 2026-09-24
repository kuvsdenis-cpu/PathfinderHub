package com.pathfinder.hub.data.local.entity.auth
import androidx.room.*
import java.util.Date

@Entity(tableName = "parent_child_links", primaryKeys = ["parentId", "childId"])
data class ParentChildLinkEntity(
    val parentId: String, val childId: String, val clubId: String,
    val relationship: String, val status: String, val linkedBy: String,
    val linkedAt: Date, val revokedAt: Date?, val revokedReason: String?
)
