package com.pathfinder.hub.data.reports

import java.util.Date

data class ReportData(
    val clubName: String,
    val generatedAt: Date,
    val generatedByName: String,

    // Общая статистика
    val totalMembers: Int,
    val teens: Int,
    val instructors: Int,
    val parents: Int,
    val secretaries: Int,

    // Прогресс по ступеням
    val levelsCompleted: Int,
    val levelRequirementsApproved: Int,
    val levelRequirementsPending: Int,

    // События
    val events: List<EventRow>,

    // QR-квесты
    val quests: List<QuestRow>,

    // Специализации
    val honorsCompleted: Int,
    val honorRequirementsApproved: Int,
    val honorRequirementsPending: Int,
)

data class EventRow(
    val title: String,
    val dateStart: Date,
    val location: String,
    val consentsApproved: Int,
    val tasksAssigned: Int,
    val stationVisits: Int,
)

data class QuestRow(
    val title: String,
    val totalSteps: Int,
    val started: Int,
    val completed: Int,
)
