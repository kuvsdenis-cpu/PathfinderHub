package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "test_attempts", indices = [Index("userId"), Index("honorId")])
data class TestAttemptEntity(
    @PrimaryKey val id: String, val userId: String, val honorId: String,
    val score: Int, val passed: Boolean, val attemptNumber: Int,
    val startedAt: Date, val completedAt: Date, val pendingSync: Boolean = false
)
