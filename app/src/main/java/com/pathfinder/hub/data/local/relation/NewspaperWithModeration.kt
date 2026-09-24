package com.pathfinder.hub.data.local.relation
import androidx.room.*
import com.pathfinder.hub.data.local.entity.newspaper.*

data class NewspaperWithModeration(
    @Embedded val issue: NewspaperIssueEntity,
    @Relation(parentColumn = "id", entityColumn = "issueId")
    val moderation: NewspaperModerationEntity?,
    @Relation(parentColumn = "id", entityColumn = "issueId")
    val publication: NewspaperPublicationEntity?
)
