package com.pathfinder.hub.data.local.relation
import androidx.room.*
import com.pathfinder.hub.data.local.entity.learning.*

data class HonorWithRequirements(
    @Embedded val honor: HonorEntity,
    @Relation(parentColumn = "id", entityColumn = "honorId")
    val requirements: List<HonorRequirementEntity>
)
