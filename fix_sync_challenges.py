#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Firestore sync for challenges - 08.10.2026
Запуск:
    python fix_sync_challenges.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        print("[SKIP] " + path + " (уже есть)")
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


def patch(path, repls, skip_if_marker=None):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

    if skip_if_marker and skip_if_marker in text:
        print("[SKIP] " + path + " (уже пропатчен)")
        return

    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)

    for i, (o, n) in enumerate(repls, 1):
        if o not in text:
            die(path + ": шаблон #" + str(i) + ":\n" + o[:400])
        text = text.replace(o, n, 1)
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. FirestoreChallengeDataSource.kt
# ============================================================
DS = '''package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.pathfinder.hub.data.local.entity.gamification.ChallengeEntity
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для челленджей и прогресса.
 *
 * Коллекции:
 *   clubs/{clubId}/challenges/{challengeId}
 *   clubs/{clubId}/challengeProgress/{challengeId}_{userId}
 *
 * Даты → Timestamp. Composite id для прогресса гарантирует,
 * что у одного пользователя — одна запись на челлендж.
 */
@Singleton
class FirestoreChallengeDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreChDS"
        const val COL_CHALLENGES = "challenges"
        const val COL_PROGRESS = "challengeProgress"

        fun progressId(challengeId: String, userId: String): String =
            "${challengeId}_${userId}"
    }

    private fun challengesRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COL_CHALLENGES)

    private fun progressRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COL_PROGRESS)

    suspend fun upsertChallenge(c: ChallengeEntity): Result<Unit> = try {
        if (c.clubId.isBlank()) throw IllegalArgumentException("clubId пустой")
        challengesRef(c.clubId).document(c.id).set(mapChallenge(c)).await()
        Log.d(TAG, "challenge upsert OK: ${c.clubId}/${c.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "challenge upsert FAILED: ${c.id}", e)
        Result.failure(e)
    }

    suspend fun upsertProgress(clubId: String, p: ChallengeProgressEntity): Result<Unit> = try {
        if (clubId.isBlank()) throw IllegalArgumentException("clubId пустой")
        val id = progressId(p.challengeId, p.userId)
        progressRef(clubId).document(id).set(mapProgress(p, clubId)).await()
        Log.d(TAG, "progress upsert OK: $clubId/$id")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "progress upsert FAILED: ${p.challengeId}", e)
        Result.failure(e)
    }

    private fun mapChallenge(c: ChallengeEntity): Map<String, Any?> = mapOf(
        "id" to c.id,
        "clubId" to c.clubId,
        "title" to c.title,
        "description" to c.description,
        "type" to c.type,
        "goal" to c.goal,
        "unit" to c.unit,
        "dateStart" to Timestamp(c.dateStart),
        "dateEnd" to Timestamp(c.dateEnd),
        "createdBy" to c.createdBy,
        "status" to c.status,
        "participants" to c.participants,
        "rewards" to c.rewards,
    )

    private fun mapProgress(p: ChallengeProgressEntity, clubId: String): Map<String, Any?> = mapOf(
        "challengeId" to p.challengeId,
        "userId" to p.userId,
        "clubId" to clubId,
        "progress" to p.progress,
        "completedAt" to p.completedAt?.let { Timestamp(it) },
        "rewardClaimed" to p.rewardClaimed,
    )
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/remote/FirestoreChallengeDataSource.kt", DS)


# ============================================================
# 2. JoinChallengeUseCase.kt — REWRITE
# ============================================================
JOIN = '''package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import com.pathfinder.hub.data.remote.FirestoreChallengeDataSource
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import javax.inject.Inject

/**
 * Присоединение к челленджу.
 *
 * Пишем в Room (AchievementRepository), затем в Firestore.
 * Если Firestore упал — только Room, SyncWorker повторит.
 */
class JoinChallengeUseCase @Inject constructor(
    private val repo: AchievementRepository,
    private val userRepository: UserRepository,
    private val firestore: FirestoreChallengeDataSource,
) {
    suspend operator fun invoke(challengeId: String, userId: String): Result<Unit> = try {
        repo.upsertChallengeProgress(
            ChallengeProgressEntity(
                challengeId = challengeId,
                userId = userId,
                progress = 0,
                completedAt = null,
                rewardClaimed = false,
                pendingSync = true,
            )
        )

        val user = userRepository.getUser(userId)
        val clubId = user?.clubId
        if (!clubId.isNullOrBlank()) {
            firestore.upsertProgress(
                clubId,
                ChallengeProgressEntity(
                    challengeId = challengeId,
                    userId = userId,
                    progress = 0,
                    completedAt = null,
                    rewardClaimed = false,
                    pendingSync = false,
                )
            )
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
'''
p = ROOT / "app/src/main/java/com/pathfinder/hub/domain/usecase/gamification/JoinChallengeUseCase.kt"
if p.exists():
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    p.write_text(JOIN, encoding="utf-8", newline="\n")
    print("[REWRITE] " + str(p))
else:
    write_new("app/src/main/java/com/pathfinder/hub/domain/usecase/gamification/JoinChallengeUseCase.kt", JOIN)


# ============================================================
# 3. UpdateChallengeProgressUseCase.kt — REWRITE
# ============================================================
UPDATE = '''package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import com.pathfinder.hub.data.remote.FirestoreChallengeDataSource
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Обновление прогресса челленджа.
 *
 * completedAt и rewardClaimed СОХРАНЯЮТСЯ из существующей записи,
 * чтобы повторный апдейт не сбрасывал завершение.
 */
class UpdateChallengeProgressUseCase @Inject constructor(
    private val repo: AchievementRepository,
    private val userRepository: UserRepository,
    private val firestore: FirestoreChallengeDataSource,
) {
    suspend operator fun invoke(
        challengeId: String,
        userId: String,
        progress: Int,
    ): Result<Unit> = try {
        val existing = repo.observeChallengeProgress(challengeId).first()
            .find { it.userId == userId }

        val updated = ChallengeProgressEntity(
            challengeId = challengeId,
            userId = userId,
            progress = progress,
            completedAt = existing?.completedAt,
            rewardClaimed = existing?.rewardClaimed ?: false,
            pendingSync = true,
        )
        repo.upsertChallengeProgress(updated)

        val user = userRepository.getUser(userId)
        val clubId = user?.clubId
        if (!clubId.isNullOrBlank()) {
            firestore.upsertProgress(clubId, updated.copy(pendingSync = false))
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
'''
p = ROOT / "app/src/main/java/com/pathfinder/hub/domain/usecase/gamification/UpdateChallengeProgressUseCase.kt"
if p.exists():
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    p.write_text(UPDATE, encoding="utf-8", newline="\n")
    print("[REWRITE] " + str(p))
else:
    write_new("app/src/main/java/com/pathfinder/hub/domain/usecase/gamification/UpdateChallengeProgressUseCase.kt", UPDATE)


# ============================================================
# 4. ClaimRewardUseCase.kt (новый) — выдача призов
# ============================================================
CLAIM = '''package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import com.pathfinder.hub.data.remote.FirestoreChallengeDataSource
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject

/**
 * Пометка приза полученным + выдача ачивок из rewards.
 */
class ClaimRewardUseCase @Inject constructor(
    private val repo: AchievementRepository,
    private val userRepository: UserRepository,
    private val firestore: FirestoreChallengeDataSource,
) {
    /**
     * Возвращает: (achievementIds, textRewards) — что выдали.
     */
    suspend operator fun invoke(
        challengeId: String,
        userId: String,
    ): Result<Pair<List<String>, List<String>>> = try {
        val challenge = repo.observeActiveChallenges(
            userRepository.getUser(userId)?.clubId ?: ""
        ).first().find { it.id == challengeId } ?: return Result.failure(
            IllegalArgumentException("Челлендж не найден")
        )

        val existing = repo.observeChallengeProgress(challengeId).first()
            .find { it.userId == userId }
            ?: return Result.failure(IllegalStateException("Нет записи прогресса"))

        if (existing.rewardClaimed) {
            return Result.success(emptyList<String>() to emptyList())
        }

        // 1) Помечаем приз полученным
        val updated = existing.copy(rewardClaimed = true, pendingSync = true)
        repo.upsertChallengeProgress(updated)
        userRepository.getUser(userId)?.clubId?.let { clubId ->
            firestore.upsertProgress(clubId, updated.copy(pendingSync = false))
        }

        // 2) Разделяем rewards на ачивки и текст
        val knownIds = repo.observeAll().first().map { it.id }.toSet()
        val (achIds, textRewards) = challenge.rewards.partition { it in knownIds }

        for (achId in achIds) {
            repo.upsertProgress(
                AchievementProgressEntity(
                    userId = userId,
                    achievementId = achId,
                    status = "earned",
                    earnedAt = Date(),
                    revokedAt = null,
                    revokedBy = null,
                    revokeReason = null,
                    pendingSync = false,
                )
            )
        }

        Result.success(achIds to textRewards)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/domain/usecase/gamification/ClaimRewardUseCase.kt", CLAIM)


# ============================================================
# 5. SyncWorker.kt — 2 процессора
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/sync/SyncWorker.kt", [
    ("import com.pathfinder.hub.data.remote.FirestoreQrDataSource",
     "import com.pathfinder.hub.data.remote.FirestoreQrDataSource\n"
     "import com.pathfinder.hub.data.remote.FirestoreChallengeDataSource"),

    ("""    private val qrDataSource: FirestoreQrDataSource,
    private val qrDao: QrStationDao,
) : CoroutineWorker(context, params) {""",
     """    private val qrDataSource: FirestoreQrDataSource,
    private val qrDao: QrStationDao,
    private val challengeDataSource: FirestoreChallengeDataSource,
) : CoroutineWorker(context, params) {"""),

    ("""            if (item.entityType == "station_visit") {
                return processStationVisit(item)
            }""",
     """            if (item.entityType == "station_visit") {
                return processStationVisit(item)
            }
            if (item.entityType == "challenge_progress") {
                return processChallengeProgress(item)
            }"""),

    ("""            "qr_station" -> if (clubId != null) "clubs/$clubId/qrStations" else null
            "station_visit" -> if (clubId != null) "clubs/$clubId/stationVisits" else null""",
     """            "qr_station" -> if (clubId != null) "clubs/$clubId/qrStations" else null
            "station_visit" -> if (clubId != null) "clubs/$clubId/stationVisits" else null
            "challenge_progress" -> if (clubId != null) "clubs/$clubId/challengeProgress" else null"""),

    ("""    private suspend fun processQrStation(item: SyncQueueItemEntity): ProcessResult {""",
     """    private suspend fun processChallengeProgress(item: SyncQueueItemEntity): ProcessResult {
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

    private suspend fun processQrStation(item: SyncQueueItemEntity): ProcessResult {"""),
], skip_if_marker="processChallengeProgress")


# ============================================================
# 6. firestore.rules
# ============================================================
patch("firestore.rules", [
    ("""      match /qrStations/{stationId} {""",
     """      match /challenges/{challengeId} {
        allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
        allow create, update: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow delete: if isSignedIn() && (isDirector() && isOwnClub(clubId));
      }

      match /challengeProgress/{progressId} {
        allow read: if isSignedIn() && (
          resource.data.userId == uid()
          || (isClubStaff() && isOwnClub(clubId))
          || isConferenceAdmin()
        );
        allow create, update: if isSignedIn()
          && request.resource.data.userId == uid()
          && request.resource.data.clubId == clubId;
        allow delete: if false;
      }

      match /qrStations/{stationId} {"""),
], skip_if_marker="match /challenges/")


print()
print("=" * 60)
print("Готово.")
print("=" * 60)
print()
print("Дальше:")
print("  1. Сборка:")
print("       .\\gradlew assembleDebug")
print("       .\\gradlew installDebug")
print("  2. Задеплой правила:")
print("       firebase deploy --only firestore:rules")
print()
print("ВАЖНО: ClaimRewardUseCase создан, но в ChallengeDetailViewModel")
print("сейчас используется инлайн-логика claimReward(). Чтобы её заменить")
print("на новый use-case — нужен отдельный патч. Пока оба работают.")
print()
print("Проверка:")
print("  - Teen присоединился к челленджу → Firestore: clubs/{clubId}/challengeProgress/{challengeId}_{userId}")
print("  - Обновил прогресс → та же запись обновилась")
print("  - С другого устройства тот же user видит прогресс (после sync)")
print()
print("Откат: .bak.<timestamp>")