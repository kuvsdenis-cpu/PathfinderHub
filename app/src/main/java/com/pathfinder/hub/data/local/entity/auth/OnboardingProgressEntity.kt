package com.pathfinder.hub.data.local.entity.auth
import androidx.room.*
import java.util.Date

@Entity(tableName = "onboarding_progress")
data class OnboardingProgressEntity(
    @PrimaryKey val userId: String, val role: String,
    val completedSlides: List<Int>, val completedAt: Date?,
    val skipped: Boolean = false
)
