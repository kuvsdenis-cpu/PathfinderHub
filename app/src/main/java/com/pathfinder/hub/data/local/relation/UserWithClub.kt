package com.pathfinder.hub.data.local.relation
import androidx.room.*
import com.pathfinder.hub.data.local.entity.core.*

data class UserWithClub(
    @Embedded val user: UserEntity,
    @Relation(parentColumn = "clubId", entityColumn = "id")
    val club: ClubEntity?
)
