package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String, val title: String, val author: String,
    val level: Int, val description: String?
)
