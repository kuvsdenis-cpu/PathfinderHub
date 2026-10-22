package com.pathfinder.hub.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.gson.Gson
import com.pathfinder.hub.data.local.dao.ModerationDao
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import com.pathfinder.hub.data.local.entity.planning.CommentEntity
import com.pathfinder.hub.data.remote.FirestoreCommentDataSource
import com.pathfinder.hub.data.local.dao.RequirementMaterialDao
import com.pathfinder.hub.data.local.dao.RequirementTaskDao
import com.pathfinder.hub.data.local.dao.RequirementTaskAnswerDao
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.remote.FirestoreRequirementContentDataSource
import com.pathfinder.hub.data.remote.FirestoreTaskAnswerDataSource
import com.pathfinder.hub.data.remote.FirestoreQrDataSource
import com.pathfinder.hub.data.remote.FirestoreChallengeDataSource
import com.pathfinder.hub.data.remote.FirestoreBookDataSource
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity
import com.pathfinder.hub.data.local.entity.gamification.BadgeItem
import com.pathfinder.hub.data.local.entity.gamification.ChevronItem
import com.pathfinder.hub.data.local.entity.gamification.SpecialMarkItem
import com.pathfinder.hub.data.repository.AchievementRepository
import java.util.Date
import com.pathfinder.hub.data.repository.BookRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import kotlinx.coroutines.flow.first
import com.pathfinder.hub.data.local.dao.QrStationDao
import com.pathfinder.hub.data.local.dao.BibleDao
import com.pathfinder.hub.data.local.dao.PearlDao
import com.pathfinder.hub.data.local.dao.ReadingPlanDao
import com.pathfinder.hub.data.local.entity.bible.BibleBookmarkEntity
import com.pathfinder.hub.data.local.entity.bible.PearlProgressEntity
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanProgressEntity
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeoutOrNull

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncQueue: SyncQueueManager,
    private val firestore: FirestoreSyncService,
    private val commentDataSource: FirestoreCommentDataSource,
    private val moderationDao: ModerationDao,
    private val materialDao: RequirementMaterialDao,
    private val taskDao: RequirementTaskDao,
    private val answerDao: RequirementTaskAnswerDao,
    private val contentDataSource: FirestoreRequirementContentDataSource,
    private val answerDataSource: FirestoreTaskAnswerDataSource,
    private val qrDataSource: FirestoreQrDataSource,
    private val qrDao: QrStationDao,
    private val challengeDataSource: FirestoreChallengeDataSource,
    private val bibleDao: BibleDao,
    private val pearlDao: PearlDao,
    private val readingPlanDao: ReadingPlanDao,
    private val bookDataSource: FirestoreBookDataSource,
    private val bookRepository: BookRepository,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val achievementRepository: AchievementRepository,
    private val customGroupDao: com.pathfinder.hub.data.local.dao.CustomGroupDao,
    private val exclusionDao: com.pathfinder.hub.data.local.dao.CustomGroupExclusionDao,
    private val infoArticleDao: com.pathfinder.hub.data.local.dao.InfoArticleDao,
    private val infoDataSource: com.pathfinder.hub.data.remote.FirestoreInfoDataSource,
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        Log.d(TAG, "▶️ Start sync...")

        val batch = syncQueue.getNextBatch(50)
        if (batch.isEmpty()) {
            Log.d(TAG, "✓ Queue empty")
            // Pull должен идти даже при пустой очереди,
            // иначе clubIds/pearlProgress и т.п. никогда не подтянутся.
            try {
                pullUserData()
            } catch (e: Exception) {
                Log.w(TAG, "Pull failed: ${e.message}")
            }
            return Result.success()
        }

        syncQueue.updateSyncStatus(isSyncing = true, queueSize = batch.size)

        var success = 0
        var errors = 0
        var permanentFailures = 0

        for (item in batch) {
            val result = withTimeoutOrNull(30_000L) {
                processItem(item)
            }

            when (result) {
                ProcessResult.SUCCESS -> {
                    syncQueue.remove(item.id)
                    success++
                }
                ProcessResult.RETRY -> {
                    errors++
                    val newRetry = item.retryCount + 1
                    if (newRetry >= MAX_RETRIES) {
                        syncQueue.updateStatus(
                            id = item.id,
                            status = "failed",
                            retryCount = newRetry,
                            errorMessage = "Max retries exceeded"
                        )
                        permanentFailures++
                    } else {
                        syncQueue.updateStatus(
                            id = item.id,
                            status = "pending",
                            retryCount = newRetry,
                            errorMessage = "Retry $newRetry"
                        )
                    }
                }
                ProcessResult.PERMANENT_FAILURE -> {
                    syncQueue.updateStatus(
                        id = item.id,
                        status = "failed",
                        retryCount = item.retryCount,
                        errorMessage = "Permanent failure"
                    )
                    permanentFailures++
                }
                null -> {
                    Log.w(TAG, "⏱ Timeout on item ${item.id}")
                    syncQueue.updateStatus(
                        id = item.id,
                        status = "pending",
                        retryCount = item.retryCount + 1,
                        errorMessage = "Timeout"
                    )
                    errors++
                }
            }
        }

        syncQueue.updateSyncStatus(
            isSyncing = false,
            queueSize = 0,
            lastError = when {
                permanentFailures > 0 -> "Permanent: $permanentFailures, errors: $errors"
                errors > 0 -> "Errors: $errors"
                else -> null
            }
        )

        Log.d(TAG, "✓ Sync done: success=$success, errors=$errors, permanent=$permanentFailures")

        // Pull user data from Firestore
        try {
            pullUserData()
        } catch (e: Exception) {
            Log.w(TAG, "Pull failed: ${e.message}")
        }

        return Result.success()
    }

    private suspend fun pullUserData() {
        val uid = syncQueue.getCurrentUserId() ?: return
        pullUser(uid)
        pullClubs(uid)
        pullBibleBookmarks(uid)
        pullPearlProgress(uid)
        pullReadingPlanProgress(uid)
        pullClubBooks(uid)
        pullDigitalUniform(uid)
        pullCustomGroups(uid)
        pullCustomGroupExclusions(uid)
        pullInfoArticles()
    }

    private suspend fun pullClubBooks(uid: String) {
        val clubIds = try {
            bookRepository.getClubIdsForUser(uid)
        } catch (e: Exception) {
            Log.w(TAG, "pullClubBooks: clubIds failed ${e.message}")
            return
        }
        for (clubId in clubIds) {
            try {
                val snapshot = bookDataSource.observeClubBooks(clubId).first()
                if (snapshot.hasError) continue
                for (remote in snapshot.items) {
                    val local = bookRepository.getById(remote.id)
                    if (local != null && local.pendingSync) continue
                    if (local != null && (local.createdAt ?: 0L) >= (remote.createdAt ?: 0L)) continue
                    bookRepository.upsert(remote)
                }
            } catch (e: Exception) {
                Log.w(TAG, "pullClubBooks skip $clubId: ${e.message}")
            }
        }
    }

        private suspend fun pullUser(uid: String) {
        val r = firestore.pullDocument("users", uid)
        val doc = r.getOrNull() ?: run {
            Log.d(TAG, "pullUser: users/$uid не найден в Firestore")
            return
        }
        try {
            val remoteClubIds = (doc["clubIds"] as? List<*>)
                ?.filterIsInstance<String>()?.filter { it.isNotBlank() }
                ?: emptyList()
            val remoteClubId = (doc["clubId"] as? String)?.takeIf { it.isNotBlank() }

            val local = userRepository.getUserLocalOnly(uid)
            if (local == null) {
                Log.d(TAG, "pullUser: local user $uid НЕ НАЙДЕН")
                return
            }
            Log.d(TAG, "pullUser: local.clubIds=${local.clubIds}, local.clubId=${local.clubId}")
            Log.d(TAG, "pullUser: remoteClubIds=$remoteClubIds, remoteClubId=$remoteClubId")

            val mergedClubIds = (local.clubIds + remoteClubIds).distinct()
            val mergedClubId = local.clubId
                ?: remoteClubId
                ?: mergedClubIds.firstOrNull()

            Log.d(TAG, "pullUser: mergedClubIds=$mergedClubIds, mergedClubId=$mergedClubId")

            if (mergedClubIds != local.clubIds || mergedClubId != local.clubId) {
                Log.d(TAG, "pullUser: ОБНОВЛЯЕМ local -> $mergedClubIds / $mergedClubId")
                userRepository.update(local.copy(
                    clubIds = mergedClubIds, clubId = mergedClubId,
                ))
            } else {
                Log.d(TAG, "pullUser: без изменений")
            }

            // Дополнительно: проверяем, что клубы есть в Room
            for (cid in mergedClubIds) {
                val club = try { userRepository.getUserLocalOnly(uid) } catch (_: Exception) { null }
                // Не используем clubRepository здесь, чтобы не менять конструктор.
                // Просто логируем факт наличия id.
                Log.d(TAG, "pullUser: clubId=$cid (см. ClubContextViewModel)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "pullUser skip: ${e.message}")
        }
    }

    /**
     * Тянет клубы пользователя из Firestore в Room.
     * Берёт clubIds из user (после pullUser), для каждого делает
     * pullDocument("clubs", clubId), маппит в ClubEntity и upsert.
     */
    private suspend fun pullClubs(uid: String) {
        val user = userRepository.getUserLocalOnly(uid) ?: return
        val ids = user.clubIds.ifEmpty { listOfNotNull(user.clubId) }
        if (ids.isEmpty()) {
            Log.d(TAG, "pullClubs: у $uid нет клубов")
            return
        }
        for (clubId in ids) {
            try {
                val local = clubRepository.getClub(clubId)
                val r = firestore.pullDocument("clubs", clubId)
                val doc = r.getOrNull() ?: run {
                    Log.d(TAG, "pullClubs: clubs/$clubId не найден в Firestore")
                    continue
                }
                // Не перезаписываем, если локально свежее (по updatedAt)
                val remoteUpdatedAt = parseDate(doc["updatedAt"])?.time ?: 0L
                if (local != null && (local.updatedAt.time >= remoteUpdatedAt)) {
                    Log.d(TAG, "pullClubs: $clubId локально свежее — skip")
                    continue
                }
                val club = mapToClubEntity(clubId, doc)
                clubRepository.upsert(club)
                Log.d(TAG, "pullClubs: $clubId -> \"${club.name}\"")
            } catch (e: Exception) {
                Log.w(TAG, "pullClubs skip $clubId: ${e.message}")
            }
        }
    }

    private fun mapToClubEntity(id: String, d: Map<String, Any?>): ClubEntity {
        fun str(k: String, def: String = ""): String = (d[k] as? String) ?: def
        fun bool(k: String, def: Boolean = false): Boolean = (d[k] as? Boolean) ?: def
        fun int(k: String, def: Int = 0): Int = (d[k] as? Number)?.toInt() ?: def
        fun long(k: String): Long? = (d[k] as? Number)?.toLong()
        fun date(k: String): java.util.Date? = parseDate(d[k])
        fun strList(k: String): List<String> =
            (d[k] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return ClubEntity(
            id = id,
            conferenceId = str("conferenceId"),
            name = str("name"),
            logoUrl = d["logoUrl"] as? String,
            city = str("city"),
            region = str("region"),
            contactEmail = str("contactEmail"),
            contactPhone = str("contactPhone"),
            foundedAt = date("foundedAt"),
            motto = d["motto"] as? String,
            meetingDay = str("meetingDay"),
            startTime = str("startTime"),
            endTime = str("endTime"),
            location = str("location"),
            frequency = str("frequency"),
            autoAcceptInvite = bool("autoAcceptInvite"),
            inviteLifetimeDays = int("inviteLifetimeDays"),
            requireParentalConsent = bool("requireParentalConsent"),
            maxMembers = (d["maxMembers"] as? Number)?.toInt(),
            paymentEnabled = bool("paymentEnabled"),
            membershipFee = (d["membershipFee"] as? Number)?.toDouble(),
            membershipPeriod = d["membershipPeriod"] as? String,
            paymentMethods = strList("paymentMethods"),
            paymentRemindersEnabled = bool("paymentRemindersEnabled"),
            autoModerationEnabled = bool("autoModerationEnabled"),
            reviewWindowHours = int("reviewWindowHours"),
            escalationHours = int("escalationHours"),
            defaultReportTemplateId = d["defaultReportTemplateId"] as? String,
            reportPeriods = strList("reportPeriods"),
            autoGenerateReports = bool("autoGenerateReports"),
            updatedAt = date("updatedAt") ?: java.util.Date(0),
            updatedBy = str("updatedBy"),
            syncedAt = date("syncedAt"),
            deletedAt = date("deletedAt"),
            deletedBy = d["deletedBy"] as? String,
            purgeAt = date("purgeAt"),
        )
    }

    private fun parseDate(v: Any?): java.util.Date? = when (v) {
        null -> null
        is java.util.Date -> v
        is com.google.firebase.Timestamp -> v.toDate()
        is Long -> java.util.Date(v)
        is Number -> java.util.Date(v.toLong())
        else -> null
    }

    private suspend fun pullBibleBookmarks(uid: String) {
        val r = firestore.pullCollection("users/$uid/bibleBookmarks")
        val docs = r.getOrNull() ?: return
        for (doc in docs) {
            try {
                val verseId = doc["verseId"] as? String ?: continue
                val existing = bibleDao.getBookmark(uid, verseId)
                val remoteCreated = (doc["createdAt"] as? Number)?.toLong() ?: 0L
                if (existing != null && existing.createdAt >= remoteCreated) continue
                bibleDao.upsertBookmark(
                    BibleBookmarkEntity(
                        id = "$uid:$verseId",
                        userId = uid,
                        verseId = verseId,
                        versionId = doc["versionId"] as? String ?: "rst",
                        bookId = doc["bookId"] as? String ?: "",
                        chapter = (doc["chapter"] as? Number)?.toInt() ?: 0,
                        verse = (doc["verse"] as? Number)?.toInt() ?: 0,
                        textPreview = doc["textPreview"] as? String ?: "",
                        createdAt = remoteCreated,
                        pendingSync = false,
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "pull bookmark skip: ${e.message}")
            }
        }
    }

        private suspend fun pullPearlProgress(uid: String) {
        val r = firestore.pullCollection("users/$uid/pearlProgress")
        val docs = r.getOrNull() ?: return
        for (doc in docs) {
            try {
                val pearlId = doc["pearlId"] as? String ?: continue
                val existing = pearlDao.getProgress(uid, pearlId)
                val remoteReviewed = (doc["lastReviewedAt"] as? Number)?.toLong() ?: 0L
                val remoteScore = (doc["trainingScore"] as? Number)?.toInt()
                val remoteAttempts = (doc["trainingAttempts"] as? Number)?.toInt() ?: 0
                val remoteTrainedAt = (doc["lastTrainedAt"] as? Number)?.toLong()

                val base = existing ?: PearlProgressEntity(
                    id = "$uid:$pearlId",
                    userId = uid,
                    pearlId = pearlId,
                )
                val merged = base.copy(
                    memorized = if (remoteReviewed > (base.lastReviewedAt ?: 0L))
                        doc["memorized"] as? Boolean ?: base.memorized else base.memorized,
                    memorizedAt = (doc["memorizedAt"] as? Number)?.toLong() ?: base.memorizedAt,
                    lastReviewedAt = maxOfNullable(base.lastReviewedAt, remoteReviewed)
                        ?: base.lastReviewedAt,
                    reviewCount = maxOf(base.reviewCount, (doc["reviewCount"] as? Number)?.toInt() ?: 0),
                    trainingScore = maxOfNullable(base.trainingScore, remoteScore),
                    trainingAttempts = maxOf(base.trainingAttempts, remoteAttempts),
                    lastTrainedAt = maxOfNullable(base.lastTrainedAt, remoteTrainedAt),
                    pendingSync = base.pendingSync,
                )
                if (merged != base) pearlDao.upsertProgress(merged)
            } catch (e: Exception) {
                Log.w(TAG, "pull pearl skip: ${e.message}")
            }
        }
    }

    private fun maxOfNullable(a: Int?, b: Int?): Int? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    private suspend fun pullReadingPlanProgress(uid: String) {
        val r = firestore.pullCollection("users/$uid/readingPlanProgress")
        val docs = r.getOrNull() ?: return
        for (doc in docs) {
            try {
                val dayId = doc["dayId"] as? String ?: continue
                val existing = readingPlanDao.getProgress(uid, dayId)
                val remoteCompleted = (doc["completedAt"] as? Number)?.toLong() ?: 0L
                if (existing != null && existing.completedAt >= remoteCompleted) continue
                readingPlanDao.upsertProgress(
                    ReadingPlanProgressEntity(
                        id = "$uid:$dayId",
                        userId = uid,
                        dayId = dayId,
                        completedAt = remoteCompleted,
                        pendingSync = false,
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "pull reading plan skip: ${e.message}")
            }
        }
    }

    private suspend fun processDigitalUniform(item: SyncQueueItemEntity): ProcessResult {
        val uniform = try {
            gson.fromJson(item.payload, DigitalUniformEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "digital_uniform deserialize failed", e)
            return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (uniform.userId.isBlank()) return ProcessResult.PERMANENT_FAILURE

        val collection = "users/${uniform.userId}/digitalUniform"
        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val payload = mapOf(
                    "userId" to uniform.userId,
                    "shirtColor" to uniform.shirtColor,
                    "tieColor" to uniform.tieColor,
                    "earnedBadges" to uniform.earnedBadges.map {
                        mapOf("honorId" to it.honorId, "position" to it.position, "earnedAt" to it.earnedAt.time)
                    },
                    "earnedChevrons" to uniform.earnedChevrons.map {
                        mapOf("levelId" to it.levelId, "position" to it.position, "earnedAt" to it.earnedAt.time)
                    },
                    "specialMarks" to uniform.specialMarks.map {
                        mapOf("achievementId" to it.achievementId, "icon" to it.icon, "earnedAt" to it.earnedAt.time)
                    },
                    "updatedAt" to uniform.updatedAt.time,
                )
                val r = firestore.pushDocument(collection, uniform.userId, payload)
                if (r.isSuccess) {
                    try { achievementRepository.markUniformSynced(uniform.userId, System.currentTimeMillis()) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = firestore.deleteDocument(collection, uniform.userId)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun pullDigitalUniform(uid: String) {
        val r = firestore.pullCollection("users/$uid/digitalUniform")
        val docs = r.getOrNull() ?: return
        for (doc in docs) {
            try {
                val remoteUserId = doc["userId"] as? String ?: uid
                val local = achievementRepository.getUniform(remoteUserId)
                if (local != null && local.pendingSync) continue

                val remoteBadges = (doc["earnedBadges"] as? List<*>)?.mapNotNull { raw ->
                    @Suppress("UNCHECKED_CAST")
                    val m = raw as? Map<String, Any?> ?: return@mapNotNull null
                    val honorId = m["honorId"] as? String ?: return@mapNotNull null
                    BadgeItem(
                        honorId = honorId,
                        position = (m["position"] as? Number)?.toInt() ?: 0,
                        earnedAt = Date((m["earnedAt"] as? Number)?.toLong() ?: 0L)
                    )
                } ?: emptyList()
                val remoteChevrons = (doc["earnedChevrons"] as? List<*>)?.mapNotNull { raw ->
                    @Suppress("UNCHECKED_CAST")
                    val m = raw as? Map<String, Any?> ?: return@mapNotNull null
                    val levelId = m["levelId"] as? String ?: return@mapNotNull null
                    ChevronItem(
                        levelId = levelId,
                        position = (m["position"] as? Number)?.toInt() ?: 0,
                        earnedAt = Date((m["earnedAt"] as? Number)?.toLong() ?: 0L)
                    )
                } ?: emptyList()
                val remoteMarks = (doc["specialMarks"] as? List<*>)?.mapNotNull { raw ->
                    @Suppress("UNCHECKED_CAST")
                    val m = raw as? Map<String, Any?> ?: return@mapNotNull null
                    val achievementId = m["achievementId"] as? String ?: return@mapNotNull null
                    SpecialMarkItem(
                        achievementId = achievementId,
                        icon = m["icon"] as? String ?: "",
                        earnedAt = Date((m["earnedAt"] as? Number)?.toLong() ?: 0L)
                    )
                } ?: emptyList()

                val mergedBadges = (local?.earnedBadges.orEmpty() + remoteBadges).distinctBy { it.honorId }
                val mergedChevrons = (local?.earnedChevrons.orEmpty() + remoteChevrons).distinctBy { it.levelId }
                val mergedMarks = (local?.specialMarks.orEmpty() + remoteMarks).distinctBy { it.achievementId }

                val remoteUpdatedAt = (doc["updatedAt"] as? Number)?.toLong() ?: 0L
                val baseUpdatedAt = local?.updatedAt?.time ?: 0L
                val updated = DigitalUniformEntity(
                    userId = remoteUserId,
                    shirtColor = (doc["shirtColor"] as? String) ?: local?.shirtColor ?: "#1E88E5",
                    tieColor = (doc["tieColor"] as? String) ?: local?.tieColor ?: "#FFC107",
                    earnedBadges = mergedBadges,
                    earnedChevrons = mergedChevrons,
                    specialMarks = mergedMarks,
                    updatedAt = Date(maxOf(remoteUpdatedAt, baseUpdatedAt)),
                    pendingSync = false,
                    syncedAt = Date()
                )
                achievementRepository.upsertUniform(updated)
            } catch (e: Exception) {
                Log.w(TAG, "pull digital_uniform skip: ${e.message}")
            }
        }
    }

    // ==================== CUSTOM GROUPS (B-sync) ====================

    private suspend fun processCustomGroup(item: SyncQueueItemEntity): ProcessResult {
        val group = try {
            gson.fromJson(item.payload, com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "custom_group deserialize failed", e)
            return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (group.clubId.isBlank() || group.id.isBlank()) return ProcessResult.PERMANENT_FAILURE

        val collection = "clubs/${group.clubId}/customGroups"
        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = firestore.pushDocument(collection, group.id, mapFromCustomGroup(group))
                if (r.isSuccess) {
                    try { customGroupDao.upsert(group.copy(pendingSync = false)) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = firestore.deleteDocument(collection, group.id)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun pullCustomGroups(uid: String) {
        val user = userRepository.getUserLocalOnly(uid) ?: return
        val clubIds = user.clubIds.ifEmpty { listOfNotNull(user.clubId) }
        for (clubId in clubIds) {
            try {
                val docs = firestore.pullCollection("clubs/$clubId/customGroups").getOrNull() ?: continue
                for (doc in docs) {
                    try {
                        val id = doc["id"] as? String ?: continue
                        val remote = mapToCustomGroup(id, clubId, doc)
                        val local = customGroupDao.getById(id)
                        if (local != null && local.pendingSync) continue
                        if (local != null && local.updatedAt.time >= remote.updatedAt.time) continue
                        if (local != null && local.archivedAt != null && remote.archivedAt == null) continue
                        customGroupDao.upsert(remote)
                        Log.d(TAG, "pullCustomGroups: $clubId/$id -> \"${remote.name}\"")
                    } catch (e: Exception) {
                        Log.w(TAG, "pullCustomGroups skip doc: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "pullCustomGroups skip club $clubId: ${e.message}")
            }
        }
    }

    private fun mapFromCustomGroup(g: com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity): Map<String, Any?> =
        mapOf(
            "id" to g.id,
            "clubId" to g.clubId,
            "name" to g.name,
            "description" to g.description,
            "createdBy" to g.createdBy,
            "createdAt" to g.createdAt,
            "updatedAt" to g.updatedAt,
            "ruleJson" to g.ruleJson,
            "forcedMemberIdsJson" to g.forcedMemberIdsJson,
            "archivedAt" to g.archivedAt,
            "purgeAfter" to g.purgeAfter,
            "pendingSync" to false,
        )

    private fun mapToCustomGroup(
        id: String, clubId: String, d: Map<String, Any?>,
    ): com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity =
        com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity(
            id = id,
            clubId = clubId,
            name = d["name"] as? String ?: "",
            description = d["description"] as? String ?: "",
            createdBy = d["createdBy"] as? String ?: "",
            createdAt = parseDate(d["createdAt"]) ?: java.util.Date(0),
            updatedAt = parseDate(d["updatedAt"]) ?: java.util.Date(0),
            ruleJson = d["ruleJson"] as? String ?: "{}",
            forcedMemberIdsJson = d["forcedMemberIdsJson"] as? String ?: "[]",
            archivedAt = parseDate(d["archivedAt"]),
            purgeAfter = parseDate(d["purgeAfter"]),
            pendingSync = false,
        )

    // ==================== CUSTOM GROUP EXCLUSIONS ====================

    private suspend fun processCustomGroupExclusion(item: SyncQueueItemEntity): ProcessResult {
        val excl = try {
            gson.fromJson(
                item.payload,
                com.pathfinder.hub.data.local.entity.groups.CustomGroupExclusionEntity::class.java,
            )
        } catch (e: Exception) {
            Log.e(TAG, "custom_group_exclusion deserialize failed", e)
            return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (excl.groupId.isBlank() || excl.userId.isBlank()) return ProcessResult.PERMANENT_FAILURE

        // clubId берём из группы: в exclusion его нет.
        val group = try { customGroupDao.getById(excl.groupId) } catch (_: Exception) { null }
        val clubId = group?.clubId
        if (clubId.isNullOrBlank()) {
            Log.w(TAG, "custom_group_exclusion: группа ${excl.groupId} не найдена — skip")
            return ProcessResult.PERMANENT_FAILURE
        }

        val collection = "clubs/$clubId/customGroups/${excl.groupId}/exclusions"
        return when (item.operation.lowercase()) {
            "upsert", "create", "update" -> {
                val payload = mapOf(
                    "groupId" to excl.groupId,
                    "userId" to excl.userId,
                    "excludedAt" to excl.excludedAt,
                    "excludedBy" to excl.excludedBy,
                    "reason" to excl.reason,
                    "pendingSync" to false,
                )
                val r = firestore.pushDocument(collection, excl.userId, payload)
                if (r.isSuccess) {
                    try { exclusionDao.markSynced(excl.groupId, excl.userId) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = firestore.deleteDocument(collection, excl.userId)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    /**
     * Тянет исключения по всем группам всех клубов пользователя.
     * Merge по (groupId, userId): LWW по excludedAt, pendingSync локально не сбрасываем.
     */
    private suspend fun pullCustomGroupExclusions(uid: String) {
        val user = userRepository.getUserLocalOnly(uid) ?: return
        val clubIds = user.clubIds.ifEmpty { listOfNotNull(user.clubId) }
        for (clubId in clubIds) {
            try {
                val groups = customGroupDao.observeActiveByClub(clubId).first()
                for (g in groups) {
                    try {
                        val docs = firestore
                            .pullCollection("clubs/$clubId/customGroups/${g.id}/exclusions")
                            .getOrNull() ?: continue
                        for (doc in docs) {
                            try {
                                val userId = doc["userId"] as? String ?: continue
                                val local = exclusionDao.getExclusion(g.id, userId)
                                if (local != null && local.pendingSync) continue
                                val remoteAt = parseDate(doc["excludedAt"]) ?: java.util.Date(0)
                                if (local != null && local.excludedAt.time >= remoteAt.time) continue
                                exclusionDao.upsert(
                                    com.pathfinder.hub.data.local.entity.groups.CustomGroupExclusionEntity(
                                        groupId = g.id,
                                        userId = userId,
                                        excludedAt = remoteAt,
                                        excludedBy = doc["excludedBy"] as? String ?: userId,
                                        reason = doc["reason"] as? String ?: "left",
                                        pendingSync = false,
                                    )
                                )
                                Log.d(TAG, "pullCustomGroupExclusions: ${g.id}/$userId")
                            } catch (e: Exception) {
                                Log.w(TAG, "pullCustomGroupExclusions skip doc: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "pullCustomGroupExclusions skip group ${g.id}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "pullCustomGroupExclusions skip club $clubId: ${e.message}")
            }
        }
    }

    // ==================== INFO ARTICLES ====================

    private suspend fun processInfoArticle(item: SyncQueueItemEntity): ProcessResult {
        val entity = try {
            gson.fromJson(item.payload, com.pathfinder.hub.data.local.entity.info.InfoArticleEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "info_article deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE

        val collection = "info_articles"
        return when (item.operation.lowercase()) {
            "upsert", "create", "update" -> {
                val r = infoDataSource.push(entity)
                if (r.isSuccess) {
                    try { infoArticleDao.upsert(entity.copy(pendingSync = false)) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                // Soft delete: обновляем документ в Firestore, ставя deletedAt
                val payload = mapOf("deletedAt" to System.currentTimeMillis())
                val r = firestore.pushDocument(collection, item.entityId, payload)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun pullInfoArticles() {
        try {
            val list = infoDataSource.pullAll()
            for (remote in list) {
                val local = infoArticleDao.getById(remote.id)
                if (local == null || local.updatedAt.time < remote.updatedAt.time) {
                    infoArticleDao.upsert(remote)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "pullInfoArticles skip: ${e.message}")
        }
    }

    private enum class ProcessResult {
        SUCCESS,
        RETRY,
        PERMANENT_FAILURE
    }

    private suspend fun processItem(item: SyncQueueItemEntity): ProcessResult {
        return try {
            // ---------- Особый случай: комментарии ----------
            // Отправляем через FirestoreCommentDataSource, а не pushDocument:
            // только он корректно конвертирует Date → Timestamp и совпадает
            // с кодом sendComment в ModerationRepository.
            if (item.entityType == "comment") {
                return processComment(item)
            }
            if (item.entityType == "requirement_material") {
                return processRequirementMaterial(item)
            }
            if (item.entityType == "requirement_task") {
                return processRequirementTask(item)
            }
            if (item.entityType == "requirement_task_answer") {
                return processRequirementTaskAnswer(item)
            }
            if (item.entityType == "qr_station") {
                return processQrStation(item)
            }
            if (item.entityType == "station_visit") {
                return processStationVisit(item)
            }
            if (item.entityType == "challenge_progress") {
                return processChallengeProgress(item)
            }
            if (item.entityType == "bible_bookmark") {
                return processBibleBookmark(item)
            }
            if (item.entityType == "pearl_progress") {
                return processPearlProgress(item)
            }
            if (item.entityType == "reading_plan_progress") {
                return processReadingPlanProgress(item)
            }
            if (item.entityType == "book") {
                return processBook(item)
            }
            if (item.entityType == "digital_uniform") {
                return processDigitalUniform(item)
            }
            if (item.entityType == "custom_group") {
                return processCustomGroup(item)
            }
            if (item.entityType == "custom_group_exclusion") {
                return processCustomGroupExclusion(item)
            }
            if (item.entityType == "info_article") {
                return processInfoArticle(item)
            }

            // ---------- Общая ветка для остальных сущностей ----------
            @Suppress("UNCHECKED_CAST")
            val payload = gson.fromJson(item.payload, Map::class.java) as? Map<String, Any?>
                ?: emptyMap()

            val collectionPath = resolveCollectionPath(item, payload)
            if (collectionPath == null) {
                Log.w(TAG, "❌ Unknown entityType: ${item.entityType}")
                return ProcessResult.PERMANENT_FAILURE
            }

            val result = when (item.operation.lowercase()) {
                "create", "update", "upsert" -> firestore.pushDocument(
                    collection = collectionPath,
                    documentId = item.entityId,
                    data = payload
                )
                "delete" -> firestore.deleteDocument(
                    collection = collectionPath,
                    documentId = item.entityId
                )
                else -> {
                    Log.w(TAG, "❌ Unknown operation: ${item.operation}")
                    return ProcessResult.PERMANENT_FAILURE
                }
            }

            if (result.isSuccess) {
                ProcessResult.SUCCESS
            } else {
                val error = result.exceptionOrNull()
                classifyError(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Process error ${item.id}", e)
            classifyError(e)
        }
    }

    /**
     * Отправка комментария через FirestoreCommentDataSource.
     *
     * 1. Десериализуем payload в CommentEntity (не в Map!).
     * 2. Отправляем через sendComment — единый код с ModerationRepository.
     * 3. При успехе — снимаем pendingSync в Room.
     */
    private suspend fun processComment(item: SyncQueueItemEntity): ProcessResult {
        val comment = try {
            gson.fromJson(item.payload, CommentEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "❌ comment deserialize failed ${item.id}", e)
            return ProcessResult.PERMANENT_FAILURE
        }

        if (comment == null || comment.clubId.isBlank()) {
            Log.w(TAG, "❌ comment: clubId пустой ${item.id}")
            return ProcessResult.PERMANENT_FAILURE
        }

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val result = commentDataSource.sendComment(comment)
                if (result.isSuccess) {
                    // Снимаем pendingSync, чтобы UI больше не показывал «не отправлено»
                    try {
                        moderationDao.upsertComment(comment.copy(pendingSync = false))
                    } catch (e: Exception) {
                        Log.w(TAG, "comment: не удалось снять pendingSync ${comment.id}", e)
                    }
                    ProcessResult.SUCCESS
                } else {
                    classifyError(result.exceptionOrNull())
                }
            }
            "delete" -> {
                val result = commentDataSource.deleteComment(comment.clubId, comment.id)
                if (result.isSuccess) ProcessResult.SUCCESS
                else classifyError(result.exceptionOrNull())
            }
            else -> {
                Log.w(TAG, "❌ Unknown comment operation: ${item.operation}")
                ProcessResult.PERMANENT_FAILURE
            }
        }
    }

    private suspend fun processRequirementMaterial(item: SyncQueueItemEntity): ProcessResult {
        val material = try {
            gson.fromJson(item.payload, RequirementMaterialEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "material deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (material.clubId.isBlank() || material.requirementId.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = contentDataSource.upsertMaterial(material)
                if (r.isSuccess) {
                    try { materialDao.clearPendingSync(material.id) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = contentDataSource.deleteMaterial(material.clubId, material.requirementId, material.id)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processRequirementTask(item: SyncQueueItemEntity): ProcessResult {
        val task = try {
            gson.fromJson(item.payload, RequirementTaskEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "task deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (task.clubId.isBlank() || task.requirementId.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = contentDataSource.upsertTask(task)
                if (r.isSuccess) {
                    try { taskDao.clearPendingSync(task.id) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = contentDataSource.deleteTask(task.clubId, task.requirementId, task.id)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processRequirementTaskAnswer(item: SyncQueueItemEntity): ProcessResult {
        val answer = try {
            gson.fromJson(item.payload, RequirementTaskAnswerEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "answer deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (answer.clubId.isBlank() || answer.userId.isBlank() || answer.taskId.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = answerDataSource.upsertAnswer(answer)
                if (r.isSuccess) {
                    try { answerDao.clearPendingSync(answer.id) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processChallengeProgress(item: SyncQueueItemEntity): ProcessResult {
        val p = try {
            gson.fromJson(item.payload, com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "challenge_progress deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE

        val payloadMap = try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(item.payload, Map::class.java) as? Map<String, Any?>
        } catch (_: Exception) { null }
        val clubId = payloadMap?.get("clubId") as? String
            ?: return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = challengeDataSource.upsertProgress(clubId, p)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processQrStation(item: SyncQueueItemEntity): ProcessResult {
        val station = try {
            gson.fromJson(item.payload, QrStationEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "station deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (station.clubId.isBlank() || station.id.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = qrDataSource.upsertStation(station)
                if (r.isSuccess) {
                    try { qrDao.upsertStation(station.copy(pendingSync = false)) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = qrDataSource.deleteStation(station.clubId, station.id)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processStationVisit(item: SyncQueueItemEntity): ProcessResult {
        val visit = try {
            gson.fromJson(item.payload, StationVisitEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "visit deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (visit.clubId.isBlank() || visit.id.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = qrDataSource.upsertVisit(visit)
                if (r.isSuccess) {
                    try { qrDao.upsertVisit(visit.copy(pendingSync = false)) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processBibleBookmark(item: SyncQueueItemEntity): ProcessResult {
        val bm = try {
            gson.fromJson(item.payload, BibleBookmarkEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "bookmark deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (bm.userId.isBlank() || bm.verseId.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        val collection = "users/${bm.userId}/bibleBookmarks"
        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val payload = mapOf(
                    "verseId" to bm.verseId,
                    "versionId" to bm.versionId,
                    "bookId" to bm.bookId,
                    "bookName" to bm.bookName,
                    "chapter" to bm.chapter,
                    "verse" to bm.verse,
                    "textPreview" to bm.textPreview,
                    "note" to bm.note,
                    "createdAt" to bm.createdAt,
                )
                val r = firestore.pushDocument(collection, bm.verseId, payload)
                if (r.isSuccess) {
                    try {
                        bibleDao.upsertBookmark(bm.copy(pendingSync = false))
                    } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = firestore.deleteDocument(collection, bm.verseId)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processPearlProgress(item: SyncQueueItemEntity): ProcessResult {
        val p = try {
            gson.fromJson(item.payload, PearlProgressEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "pearl progress deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (p.userId.isBlank() || p.pearlId.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        val collection = "users/${p.userId}/pearlProgress"
        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val payload = mapOf(
                    "pearlId" to p.pearlId,
                    "memorized" to p.memorized,
                    "memorizedAt" to p.memorizedAt,
                    "lastReviewedAt" to p.lastReviewedAt,
                    "reviewCount" to p.reviewCount,
                    "trainingScore" to p.trainingScore,
                    "trainingAttempts" to p.trainingAttempts,
                    "lastTrainedAt" to p.lastTrainedAt,
                )
                val r = firestore.pushDocument(collection, p.pearlId, payload)
                if (r.isSuccess) {
                    try {
                        pearlDao.upsertProgress(p.copy(pendingSync = false))
                    } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = firestore.deleteDocument(collection, p.pearlId)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processReadingPlanProgress(item: SyncQueueItemEntity): ProcessResult {
        val p = try {
            gson.fromJson(item.payload, ReadingPlanProgressEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "reading plan progress deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (p.userId.isBlank() || p.dayId.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        val collection = "users/${p.userId}/readingPlanProgress"
        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val payload = mapOf(
                    "dayId" to p.dayId,
                    "completedAt" to p.completedAt,
                )
                val r = firestore.pushDocument(collection, p.dayId, payload)
                if (r.isSuccess) {
                    try {
                        readingPlanDao.upsertProgress(p.copy(pendingSync = false))
                    } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = firestore.deleteDocument(collection, p.dayId)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processBook(item: SyncQueueItemEntity): ProcessResult {
        val book = try {
            gson.fromJson(item.payload, BookEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "book deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (book.clubId.isNullOrBlank() || book.id.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = bookDataSource.pushBook(book)
                if (r.isSuccess) {
                    try { bookRepository.upsert(book.copy(pendingSync = false)) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = bookDataSource.deleteBook(book.clubId!!, book.id)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private fun classifyError(error: Throwable?): ProcessResult {
        return when (error) {
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    Log.e(TAG, "🔒 PERMISSION_DENIED — не retry")
                    ProcessResult.PERMANENT_FAILURE
                }
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                    Log.e(TAG, "🔒 UNAUTHENTICATED — не retry")
                    ProcessResult.PERMANENT_FAILURE
                }
                FirebaseFirestoreException.Code.INVALID_ARGUMENT -> {
                    Log.e(TAG, "❌ INVALID_ARGUMENT — не retry")
                    ProcessResult.PERMANENT_FAILURE
                }
                FirebaseFirestoreException.Code.NOT_FOUND -> {
                    Log.d(TAG, "ℹ️ NOT_FOUND — считаем success")
                    ProcessResult.SUCCESS
                }
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> {
                    Log.w(TAG, "🌐 ${error.code} — retry")
                    ProcessResult.RETRY
                }
                else -> {
                    Log.w(TAG, "⚠️ ${error.code} — retry")
                    ProcessResult.RETRY
                }
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown error — retry", error)
                ProcessResult.RETRY
            }
        }
    }

    /**
     * Маппинг entityType → Firestore collection path.
     *
     * Для "comment" сюда не попадаем — обрабатывается в processComment.
     * clubId из payload для остальных клубных сущностей.
     */
    private fun resolveCollectionPath(
        item: SyncQueueItemEntity,
        payload: Map<String, Any?>
    ): String? {
        val userId = item.userId
        val clubId = payload["clubId"] as? String

        return when (item.entityType) {
            // ---------- Глобальные ----------
            "user" -> "users"
            "club" -> "clubs"

            // ---------- Клубные ----------
            "event" -> if (clubId != null) "clubs/$clubId/events" else null
            "task" -> if (clubId != null) "clubs/$clubId/tasks" else null
            "assignment" -> if (clubId != null) "clubs/$clubId/assignments" else null
            "moderation_request" -> if (clubId != null) "clubs/$clubId/moderationRequests" else null
            "moderation_history" -> if (clubId != null) "clubs/$clubId/moderationHistory" else null
            "appeal" -> if (clubId != null) "clubs/$clubId/appeals" else null
            "honor_draft" -> if (clubId != null) "clubs/$clubId/honorDrafts" else null
            "report" -> if (clubId != null) "clubs/$clubId/reports" else null
            "qr_station" -> if (clubId != null) "clubs/$clubId/qrStations" else null
            "station_visit" -> if (clubId != null) "clubs/$clubId/stationVisits" else null
            "challenge_progress" -> if (clubId != null) "clubs/$clubId/challengeProgress" else null
            "conference_event" -> if (payload["conferenceId"] != null) "conferences/${payload["conferenceId"]}/events" else null
            "test" -> if (clubId != null) "clubs/$clubId/tests" else null
            "book" -> if (clubId != null) "clubs/$clubId/books" else null
            "test_question" -> {
                val testId = payload["testId"] as? String
                if (clubId != null && testId != null) "clubs/$clubId/tests/$testId/questions" else null
            }

            // ---------- Прогресс пользователя ----------
            "level_progress" -> "users/$userId/levelProgress"
            "honor_progress" -> "users/$userId/honorProgress"
            "level_completion" -> "users/$userId/levelCompletions"
            "honor_completion" -> "users/$userId/honorCompletions"
            "test_attempt" -> "users/$userId/testAttempts"
            "open_answer_review" -> "users/$userId/openAnswerReviews"
            "book_report" -> "users/$userId/bookReports"
            "verse_progress" -> "users/$userId/verseProgress"
            "bible_bookmark" -> "users/$userId/bibleBookmarks"
            "pearl_progress" -> "users/$userId/pearlProgress"
            "reading_plan_progress" -> "users/$userId/readingPlanProgress"
            "leader_checklist" -> "users/$userId/leaderChecklists"
            "notification" -> "users/$userId/notifications"
            "digital_uniform" -> "users/$userId/digitalUniform"
            "custom_group" -> if (clubId != null) "clubs/$clubId/customGroups" else null
            "custom_group_exclusion" -> null // обрабатывается в processCustomGroupExclusion

            // "comment" обрабатывается в processComment, сюда не доходит
            else -> null
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "firestore_sync"
        private const val MAX_RETRIES = 5
    }
}