package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*

@Entity(tableName = "memory_verses")
data class MemoryVerseEntity(
    @PrimaryKey val id: String, val text: String, val reference: String,
    val levelId: String?, val required: Boolean
)
