package com.pathfinder.hub.ui.screens.home.director.scout

import com.pathfinder.hub.data.local.entity.core.UserEntity
import java.util.Date

data class ScoutSummary(
    val user: UserEntity,
    val currentLevelId: String?,
    val currentLevelName: String?,
    val levelRequirementsTotal: Int,
    val levelRequirementsApproved: Int,
    val levelPercent: Int,
    val honorsInProgress: Int,
    val honorsCompleted: Int,
)

data class CompletedLevelSummary(
    val levelId: String,
    val levelName: String,
    val completedAt: Date?,
)

data class RequirementStatus(
    val id: String,
    val text: String,
    val order: Int,
    val isAdvanced: Boolean,
    val approved: Boolean,
    val submitted: Boolean,
    val approvedAt: Date?,
    val mediaUrls: List<String> = emptyList(),
)

data class HonorProgressDetail(
    val honorId: String,
    val honorName: String,
    val imageUrl: String?,
    val requirementsTotal: Int,
    val requirementsApproved: Int,
    val percent: Int,
    val requirements: List<RequirementStatus>,
    val completedAt: Date?,
)

data class ScoutDetail(
    val user: UserEntity,
    val completedLevels: List<CompletedLevelSummary>,
    val currentLevelId: String?,
    val currentLevelName: String?,
    val levelRequirements: List<RequirementStatus>,
    val honorsInProgress: List<HonorProgressDetail>,
    val honorsCompleted: List<HonorProgressDetail>,
)
