package com.pathfinder.hub.data.local.entity.newspaper
import androidx.room.*
import java.util.Date

@Entity(tableName = "newspaper_publications")
data class NewspaperPublicationEntity(
    @PrimaryKey val issueId: String, val pdfUrl: String, val pageCount: Int,
    val fileSizeBytes: Int, val generatedAt: Date, val notifiedAt: Date?
)
