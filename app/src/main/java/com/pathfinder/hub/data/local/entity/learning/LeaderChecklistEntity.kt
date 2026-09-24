package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "leader_checklists", primaryKeys = ["leaderId", "childId", "levelId"])
data class LeaderChecklistEntity(
    val leaderId: String, val childId: String, val levelId: String,
    val items: List<ChecklistItem>, val updatedAt: Date
)

data class ChecklistItem(
    val requirementId: String, val status: String,
    val comment: String?, val updatedAt: Date
)
