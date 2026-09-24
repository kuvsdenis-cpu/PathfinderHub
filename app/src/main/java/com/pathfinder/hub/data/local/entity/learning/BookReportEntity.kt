package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "book_reports")
data class BookReportEntity(
    @PrimaryKey val id: String, val userId: String, val bookId: String,
    val type: String, val content: String, val status: String,
    val approvedBy: String?, val approvedAt: Date?, val pendingSync: Boolean = false
)
