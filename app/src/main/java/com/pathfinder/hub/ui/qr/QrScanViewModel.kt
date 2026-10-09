package com.pathfinder.hub.ui.qr

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestProgressEntity
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.QrQuestRepository
import com.pathfinder.hub.data.repository.QrStationRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class QuestUpdate(
    val questId: String,
    val questTitle: String,
    val stepIndex: Int,
    val totalSteps: Int,
    val completed: Boolean,
    val rewardPoints: Int,
)

data class QuestWithProgress(
    val quest: QrQuestEntity,
    val lastCompletedStepIndex: Int,
    val totalSteps: Int,
    val completed: Boolean,
)

data class ScanResult(
    val ok: Boolean,
    val geoVerified: Boolean,
    val stationName: String,
    val stationType: String,
    val distanceMeters: Double?,
    val rewardPoints: Int,
    val message: String,
    val alreadyVisitedToday: Boolean = false,
    val questUpdates: List<QuestUpdate> = emptyList(),
)

data class QrScanUiState(
    val isProcessing: Boolean = false,
    val lastScan: ScanResult? = null,
    val errorMessage: String? = null,
    val activeQuests: List<QuestWithProgress> = emptyList(),
)

@HiltViewModel
class QrScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrRepository: QrStationRepository,
    private val qrQuestRepository: QrQuestRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QrScanUiState())
    val state: StateFlow<QrScanUiState> = _state.asStateFlow()

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    init {
        observeActiveQuests()
    }

    private fun observeActiveQuests() {
        val uid = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            val user = userRepository.getUser(uid)
            val clubId = user?.clubId ?: return@launch

            qrQuestRepository.observeActiveQuests(clubId).collect { quests ->
                val list = quests.map { q ->
                    val progress = qrQuestRepository.getProgress(q.id, uid)
                    val total = qrQuestRepository.getStepCount(q.id)
                    QuestWithProgress(
                        quest = q,
                        lastCompletedStepIndex = progress?.lastCompletedStepIndex ?: 0,
                        totalSteps = total,
                        completed = progress?.completedAt != null,
                    )
                }
                _state.update { it.copy(activeQuests = list) }
            }
        }
    }

    fun reset() {
        _state.update { it.copy(lastScan = null, errorMessage = null) }
    }

    fun onScanned(raw: String) {
        val stationId = parseStationId(raw)
        if (stationId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Неверный формат QR-кода") }
            return
        }
        processStation(stationId)
    }

    private fun parseStationId(raw: String): String? {
        val s = raw.trim()
        if (s.startsWith("PFHUB:STATION:")) return s.removePrefix("PFHUB:STATION:").trim()
        return if (s.isNotBlank() && s.length <= 64) s else null
    }

    private fun processStation(stationId: String) {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, errorMessage = null) }

            try {
                val station = qrRepository.getStation(stationId)
                if (station == null) {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Станция не найдена: " + stationId
                        )
                    }
                    return@launch
                }

                val already = qrRepository.countVisitsToday(uid, station.id) > 0

                // ---------- Локация и визит ----------
                val loc = getLocation()
                val distance: Double? = if (loc != null) {
                    QrStationRepository.distanceMeters(
                        station.latitude, station.longitude,
                        loc.latitude, loc.longitude
                    )
                } else null

                val verified = if (loc != null && distance != null) {
                    QrStationRepository.isWithinRadius(
                        station.latitude, station.longitude, station.radiusMeters,
                        loc.latitude, loc.longitude, loc.accuracy
                    )
                } else false

                val user = userRepository.getUser(uid)
                val clubId = user?.clubId ?: station.clubId

                val visit = StationVisitEntity(
                    id = UUID.randomUUID().toString(),
                    stationId = station.id,
                    stationName = station.name,
                    stationType = station.type,
                    userId = uid,
                    clubId = clubId,
                    scannedAt = Date(),
                    userLat = loc?.latitude,
                    userLon = loc?.longitude,
                    accuracyMeters = loc?.accuracy,
                    distanceMeters = distance,
                    geoVerified = verified,
                    rewardPoints = if (verified) station.rewardPoints else 0,
                    pendingSync = true,
                )
                qrRepository.upsertVisit(visit)

                // Награда за саму станцию (ачивка)
                if (verified && !station.rewardAchievementId.isNullOrBlank()) {
                    try {
                        achievementRepository.upsertProgress(
                            AchievementProgressEntity(
                                userId = uid,
                                achievementId = station.rewardAchievementId,
                                status = "earned",
                                earnedAt = Date(),
                                revokedAt = null,
                                revokedBy = null,
                                revokeReason = null,
                                pendingSync = false,
                            )
                        )
                    } catch (e: Exception) {
                        Log.w("QrScan", "achievement grant failed", e)
                    }
                }

                // ---------- Обработка квестов ----------
                val questUpdates = if (verified) {
                    processQuestsForStation(uid, clubId, station, already)
                } else emptyList()

                // ---------- Сообщение ----------
                val baseMsg = when {
                    already -> "Вы уже отмечались на этой станции сегодня"
                    verified -> "Посещение засчитано! +" + station.rewardPoints + " очков"
                    loc == null -> "Посещение сохранено, но гео не подтверждено (нет локации)"
                    else -> "Слишком далеко: " + distance?.toInt() + " м (нужно " +
                            station.radiusMeters + " м). Посещение сохранено как непроверенное."
                }

                val questMsg = if (questUpdates.isNotEmpty()) {
                    val parts = questUpdates.map { u ->
                        if (u.completed)
                            "🏆 Квест «${u.questTitle}» завершён! +${u.rewardPoints} очков"
                        else
                            "Квест «${u.questTitle}»: ${u.stepIndex}/${u.totalSteps}"
                    }
                    parts.joinToString(" · ")
                } else ""

                val finalMsg = listOf(baseMsg, questMsg)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")

                _state.update {
                    it.copy(
                        isProcessing = false,
                        lastScan = ScanResult(
                            ok = verified,
                            geoVerified = verified,
                            stationName = station.name,
                            stationType = station.type,
                            distanceMeters = distance,
                            rewardPoints = if (verified) station.rewardPoints else 0,
                            message = finalMsg,
                            alreadyVisitedToday = already,
                            questUpdates = questUpdates,
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("QrScan", "process failed", e)
                _state.update {
                    it.copy(isProcessing = false, errorMessage = e.message ?: "Ошибка")
                }
            }
        }
    }

    /**
     * Найти все квесты, в которые входит станция, обновить прогресс,
     * выдать награды за завершение.
     */
    private suspend fun processQuestsForStation(
        uid: String,
        clubId: String,
        station: QrStationEntity,
        alreadyVisited: Boolean,
    ): List<QuestUpdate> {
        val links = qrQuestRepository.getQuestsForStation(station.id)
        if (links.isEmpty()) return emptyList()

        val updates = mutableListOf<QuestUpdate>()
        val now = Date()

        for (link in links) {
            val quest = qrQuestRepository.getQuest(link.questId) ?: continue
            if (quest.status != "active") continue

            // Проверка дат
            val inRange = (quest.dateStart == null || !now.before(quest.dateStart)) &&
                    (quest.dateEnd == null || !now.after(quest.dateEnd))
            if (!inRange) continue

            val total = qrQuestRepository.getStepCount(quest.id)
            val existing = qrQuestRepository.getProgress(quest.id, uid)
            val last = existing?.lastCompletedStepIndex ?: 0

            // Если уже завершён — просто добавляем в результат
            if (existing?.completedAt != null) {
                continue
            }

            // Ожидаемый шаг?
            val expected = last + 1
            if (link.stepIndex != expected) {
                // Не тот шаг — пропускаем
                Log.d("QrScan", "quest ${quest.title}: step ${link.stepIndex}, expected $expected")
                continue
            }

            // Отмечаем шаг
            val newLast = link.stepIndex
            val newIndices = (existing?.completedStepIndices ?: emptyList()) + link.stepIndex
            val isCompleted = newLast >= total

            val updated = QrQuestProgressEntity(
                questId = quest.id,
                userId = uid,
                clubId = clubId,
                lastCompletedStepIndex = newLast,
                completedStepIndices = newIndices.distinct(),
                startedAt = existing?.startedAt ?: now,
                completedAt = if (isCompleted) now else null,
                rewardClaimed = existing?.rewardClaimed ?: false,
                pendingSync = true,
            )
            qrQuestRepository.upsertProgress(updated)

            // Награда за завершение квеста
            if (isCompleted) {
                if (quest.rewardAchievementId != null) {
                    try {
                        achievementRepository.upsertProgress(
                            AchievementProgressEntity(
                                userId = uid,
                                achievementId = quest.rewardAchievementId,
                                status = "earned",
                                earnedAt = now,
                                revokedAt = null,
                                revokedBy = null,
                                revokeReason = null,
                                pendingSync = false,
                            )
                        )
                    } catch (e: Exception) {
                        Log.w("QrScan", "quest achievement failed", e)
                    }
                }
            }

            updates.add(
                QuestUpdate(
                    questId = quest.id,
                    questTitle = quest.title,
                    stepIndex = newLast,
                    totalSteps = total,
                    completed = isCompleted,
                    rewardPoints = if (isCompleted) quest.rewardPoints else 0,
                )
            )
        }

        return updates
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocation(): Location? {
        return try {
            withTimeoutOrNull(10_000L) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }
        } catch (e: Exception) {
            Log.w("QrScan", "getLocation failed", e)
            null
        }
    }
}
