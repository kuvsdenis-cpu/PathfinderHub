package com.pathfinder.hub.data.local.entity.newspaper
import androidx.room.*
import java.util.Date

@Entity(tableName = "newspaper_issues", indices = [Index("year"), Index("status")])
data class NewspaperIssueEntity(
    @PrimaryKey val id: String, val number: Int, val year: Int,
    val title: String, val subtitle: String?, val templateId: String?,
    val level: String, val clubId: String?, val status: String,
    val sections: List<NewspaperSection>, val coverImageUrl: String?,
    val pdfUrl: String?, val pageCount: Int?, val fileSizeBytes: Int?,
    val description: String?, val isHistorical: Boolean,
    val createdBy: String?, val createdAt: Date?, val updatedAt: Date?,
    val approvedBy: String?, val approvedAt: Date?, val rejectionReason: String?,
    val publishedAt: Date?, val addedBy: String?, val addedAt: Date?
)

data class NewspaperSection(val sectionId: String, val blocks: List<NewspaperBlock>)
data class NewspaperBlock(val type: String, val content: String, val order: Int)
