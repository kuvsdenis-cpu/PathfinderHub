package com.pathfinder.hub.data.local.relation
import androidx.room.*
import com.pathfinder.hub.data.local.entity.planning.*

data class TaskWithAssignments(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val assignments: List<AssignmentEntity>
)
