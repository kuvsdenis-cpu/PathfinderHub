package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "share_cards")
data class ShareCardEntity(
    @PrimaryKey val id: String, val type: String, val targetId: String,
    val imageUrl: String, val generatedAt: Date
)
