package com.pathfinder.hub.data.reports

import com.pathfinder.hub.data.local.dao.ReportDao
import com.pathfinder.hub.data.local.entity.reports.ReportEntity
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.QrQuestRepository
import com.pathfinder.hub.data.repository.TaskRepository
import com.pathfinder.hub.data.repository.UserRepository
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportDataCollector @Inject constructor(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val eventRepository: EventRepository,
    private val qrQuestRepository: QrQuestRepository,
    private val qrStationRepository: com.pathfinder.hub.data.repository.QrStationRepository,
    private val parentRepository: ParentRepository,
    private val taskRepository: TaskRepository,
) {

    suspend fun collect(
        clubId: String,
        periodStart: Date,
        periodEnd: Date,
        report: ReportEntity,
    ): ReportData {
        val from = periodStart.time
        val to = periodEnd.time

        // Клуб и автор
        val club = clubRepository.getClub(clubId)
        val author = userRepository.getUser(report.generatedBy)

        // Участники
        val members = userRepository.observeClubMembers(clubId).first()
        val teens = members.count { it.role == "teen" }
        val instructors = members.count { it.role == "instructor" }
        val parents = members.count { it.role == "parent" }
        val secretaries = members.count { it.role == "secretary" }

        // Прогресс по ступеням — одним SQL-запросом, с учётом мультиклубности
        val levelsCompleted = levelRepository.countLevelsCompletedInPeriod(clubId, from, to)
        val levelReqApproved = levelRepository.countApprovedInPeriod(clubId, from, to)
        val levelReqPending = levelRepository.countPendingInPeriod(clubId, from, to)

        // Специализации — одним SQL-запросом, с учётом мультиклубности
        val honorsCompleted = honorRepository.countSpecializationsCompletedInPeriod(clubId, from, to)
        val honorReqApproved = honorRepository.countApprovedInPeriod(clubId, from, to)
        val honorReqPending = honorRepository.countPendingInPeriod(clubId, from, to)

        // События
        val events = eventRepository.getPublishedInPeriod(clubId, from, to)
        val eventRows = events.map { e ->
            val consentsApproved = try {
                parentRepository.observeConsentsByEvent(e.id).first()
                    .count { it.status == "approved" }
            } catch (_: Exception) { 0 }

            val tasksAssigned = try {
                taskRepository.observeEventTasks(e.id).first().size
            } catch (_: Exception) { 0 }

            EventRow(
                title = e.title,
                dateStart = e.dateStart,
                location = e.location,
                consentsApproved = consentsApproved,
                tasksAssigned = tasksAssigned,
                stationVisits = qrStationRepository.countVisitsByEvent(e.id, from, to),
            )
        }

        // QR-квесты
        val quests = qrQuestRepository.observeAllQuests(clubId).first()
        val progress = qrQuestRepository.getProgressInPeriod(clubId, from, to)
        val questRows = quests.map { q ->
            val qp = progress.filter { it.questId == q.id }
            val steps = qrQuestRepository.getStepCount(q.id)
            QuestRow(
                title = q.title,
                totalSteps = steps,
                started = qp.count { it.startedAt != null },
                completed = qp.count { it.completedAt != null },
            )
        }.filter { it.started > 0 }

        return ReportData(
            clubName = club?.name ?: "Клуб",
            generatedAt = report.generatedAt,
            generatedByName = author?.let { "${it.firstName} ${it.lastName}".trim() } ?: "—",
            totalMembers = members.size,
            teens = teens,
            instructors = instructors,
            parents = parents,
            secretaries = secretaries,
            levelsCompleted = levelsCompleted,
            levelRequirementsApproved = levelReqApproved,
            levelRequirementsPending = levelReqPending,
            events = eventRows,
            quests = questRows,
            honorsCompleted = honorsCompleted,
            honorRequirementsApproved = honorReqApproved,
            honorRequirementsPending = honorReqPending,
        )
    }
}
