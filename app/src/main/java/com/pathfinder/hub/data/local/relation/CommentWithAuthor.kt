package com.pathfinder.hub.data.local.relation
import androidx.room.*
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.planning.CommentEntity

data class CommentWithAuthor(
    @Embedded val comment: CommentEntity,
    @Relation(parentColumn = "authorId", entityColumn = "id")
    val author: UserEntity?
)
