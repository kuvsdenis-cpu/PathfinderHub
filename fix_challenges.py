#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub - Challenges end-to-end (08.10.2026)

- Замена demo_club на реальный clubId
- Автозавершение при достижении goal
- Кнопка "Получить приз" + выдача ачивок из rewards
- UpdateChallengeProgressUseCase больше не сбрасывает completedAt

Запуск:
    python fix_challenges.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m):
    print("[ERROR] " + m); sys.exit(1)


def patch(path, repls, skip_if_marker=None):
    p = ROOT / path
    if not p.exists(): die("не найден: " + path)
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
# 1. UpdateChallengeProgressUseCase.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/domain/usecase/gamification/UpdateChallengeProgressUseCase.kt", [
    ("""package com.pathfinder.hub.domain.usecase.gamification
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import javax.inject.Inject

class UpdateChallengeProgressUseCase @Inject constructor(private val repo: AchievementRepository) {
    suspend operator fun invoke(challengeId: String, userId: String, progress: Int): Result<Unit> = try {
        repo.upsertChallengeProgress(ChallengeProgressEntity(
            challengeId = challengeId, userId = userId, progress = progress,
            completedAt = null, rewardClaimed = false, pendingSync = true
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}""",
     """package com.pathfinder.hub.domain.usecase.gamification
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UpdateChallengeProgressUseCase @Inject constructor(private val repo: AchievementRepository) {
    /**
     * Обновляет прогресс, СОХРАНЯЯ completedAt и rewardClaimed
     * из существующей записи. Иначе при повторном апдейте
     * статус «завершён» сбрасывался бы.
     */
    suspend operator fun invoke(challengeId: String, userId: String, progress: Int): Result<Unit> = try {
        val existing = repo.observeChallengeProgress(challengeId).first()
            .find { it.userId == userId }
        repo.upsertChallengeProgress(ChallengeProgressEntity(
            challengeId = challengeId,
            userId = userId,
            progress = progress,
            completedAt = existing?.completedAt,
            rewardClaimed = existing?.rewardClaimed ?: false,
            pendingSync = true
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}"""),
], skip_if_marker="val existing = repo.observeChallengeProgress")


# ============================================================
# 2. ChallengesViewModel.kt — реальный clubId
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/challenges/ChallengesViewModel.kt", [
    ("import com.pathfinder.hub.data.repository.AchievementRepository",
     "import com.pathfinder.hub.data.repository.AchievementRepository\nimport com.pathfinder.hub.data.repository.UserRepository"),

    ("""class ChallengesViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val achievementRepository: AchievementRepository
) : ViewModel() {""",
     """class ChallengesViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val achievementRepository: AchievementRepository,
    private val userRepository: UserRepository
) : ViewModel() {"""),

    ("""        viewModelScope.launch {
            val clubId = "demo_club"
            achievementRepository.observeActiveChallenges(clubId).collect { challenges ->""",
     """        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            val clubId = user?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            achievementRepository.observeActiveChallenges(clubId).collect { challenges ->"""),
], skip_if_marker="userRepository.getUser")


# ============================================================
# 3. ChallengeDetailViewModel.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/challenges/ChallengeDetailViewModel.kt", [
    # Импорты
    ("import com.pathfinder.hub.data.repository.AchievementRepository",
     "import com.pathfinder.hub.data.repository.AchievementRepository\n"
     "import com.pathfinder.hub.data.repository.UserRepository\n"
     "import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity\n"
     "import java.util.Date"),

    # Инжект UserRepository
    ("""class ChallengeDetailViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val achievementRepository: AchievementRepository,
    private val joinChallengeUseCase: JoinChallengeUseCase,
    private val updateChallengeProgressUseCase: UpdateChallengeProgressUseCase
) : ViewModel() {""",
     """class ChallengeDetailViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val achievementRepository: AchievementRepository,
    private val userRepository: UserRepository,
    private val joinChallengeUseCase: JoinChallengeUseCase,
    private val updateChallengeProgressUseCase: UpdateChallengeProgressUseCase
) : ViewModel() {"""),

    # loadChallenge — реальный clubId
    ("""        viewModelScope.launch {
            val clubId = "demo_club"
            achievementRepository.observeActiveChallenges(clubId).collect { challenges ->
                val challenge = challenges.find { it.id == challengeId }
                _state.update { it.copy(challenge = challenge, isLoading = false) }
            }
        }""",
     """        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            val clubId = user?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            achievementRepository.observeActiveChallenges(clubId).collect { challenges ->
                val challenge = challenges.find { it.id == challengeId }
                _state.update { it.copy(challenge = challenge, isLoading = false) }
            }
        }"""),

    # updateProgress — проверка завершения
    ("""    fun updateProgress(newProgress: Int) {
        val userId = sessionManager.getUserId() ?: return
        val challengeId = _state.value.challenge?.id ?: return

        viewModelScope.launch {
            _state.update { it.copy(isUpdatingProgress = true) }
            // ВАЖНО: порядок параметров — challengeId, userId, progress
            updateChallengeProgressUseCase(challengeId, userId, newProgress)
                .onSuccess {
                    _state.update {
                        it.copy(isUpdatingProgress = false, successMessage = "Прогресс обновлен!")
                    }
                    loadChallenge(challengeId)
                }
                .onFailure { e ->
                    _state.update { it.copy(isUpdatingProgress = false, errorMessage = e.message) }
                }
        }
    }""",
     """    fun updateProgress(newProgress: Int) {
        val userId = sessionManager.getUserId() ?: return
        val challenge = _state.value.challenge ?: return
        val challengeId = challenge.id

        viewModelScope.launch {
            _state.update { it.copy(isUpdatingProgress = true) }
            // ВАЖНО: порядок параметров — challengeId, userId, progress
            updateChallengeProgressUseCase(challengeId, userId, newProgress)
                .onSuccess {
                    // Достигли цели — фиксируем завершение
                    if (newProgress >= challenge.goal) {
                        markCompletedIfNeeded(challenge, userId)
                    }
                    _state.update {
                        it.copy(isUpdatingProgress = false, successMessage = "Прогресс обновлен!")
                    }
                    loadChallenge(challengeId)
                }
                .onFailure { e ->
                    _state.update { it.copy(isUpdatingProgress = false, errorMessage = e.message) }
                }
        }
    }

    /**
     * Ставит completedAt, если его ещё нет.
     * НЕ выдаёт призы — это отдельная кнопка claimReward().
     */
    private suspend fun markCompletedIfNeeded(
        challenge: ChallengeEntity,
        userId: String,
    ) {
        val existing = achievementRepository.observeChallengeProgress(challenge.id).first()
            .find { it.userId == userId } ?: return
        if (existing.completedAt != null) return

        achievementRepository.upsertChallengeProgress(
            existing.copy(
                progress = challenge.goal,
                completedAt = Date(),
                rewardClaimed = false,
                pendingSync = true
            )
        )
        _state.update { it.copy(successMessage = "🏆 Челлендж завершён!") }
    }

    /**
     * Кнопка «Получить приз».
     *
     * Если в rewards лежат ID существующих ачивок — выдаём их
     * через upsertProgress. Текстовые призы показываем в снекбаре.
     */
    fun claimReward() {
        val userId = sessionManager.getUserId() ?: return
        val challenge = _state.value.challenge ?: return
        val progress = _state.value.progress ?: return
        if (progress.completedAt == null || progress.rewardClaimed) return

        viewModelScope.launch {
            try {
                // 1) Помечаем приз полученным
                achievementRepository.upsertChallengeProgress(
                    progress.copy(rewardClaimed = true, pendingSync = true)
                )

                // 2) Разделяем rewards на ачивки и текстовые
                val knownIds = achievementRepository.observeAll().first()
                    .map { it.id }.toSet()
                val (achIds, textRewards) = challenge.rewards.partition { it in knownIds }

                // 3) Выдаём ачивки напрямую
                for (achId in achIds) {
                    achievementRepository.upsertProgress(
                        AchievementProgressEntity(
                            userId = userId,
                            achievementId = achId,
                            status = "earned",
                            earnedAt = Date(),
                            revokedAt = null,
                            revokedBy = null,
                            revokeReason = null,
                            pendingSync = false
                        )
                    )
                }

                // 4) Снекбар
                val msg = buildString {
                    append("🏆 Призы получены")
                    if (textRewards.isNotEmpty()) {
                        append(": ")
                        append(textRewards.joinToString(", "))
                    }
                }
                _state.update { it.copy(successMessage = msg) }
                loadChallenge(challenge.id)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка выдачи приза") }
            }
        }
    }"""),
], skip_if_marker="fun claimReward")


# ============================================================
# 4. ChallengeDetailScreen.kt — кнопка "Получить приз"
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/challenges/ChallengeDetailScreen.kt", [
    # В блок с прогрессом — добавить секцию призов
    ("""                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.updateProgress(progress.progress + 1) },
                            enabled = !state.isUpdatingProgress && progress.progress < challenge.goal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isUpdatingProgress) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Обновить прогресс (+1)")
                            }
                        }
                    }
                }""",
     """                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.updateProgress(progress.progress + 1) },
                            enabled = !state.isUpdatingProgress && progress.progress < challenge.goal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isUpdatingProgress) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Обновить прогресс (+1)")
                            }
                        }

                        // ---------- Завершение челленджа ----------
                        val isCompleted = progress.completedAt != null
                        val isRewardClaimed = progress.rewardClaimed

                        if (isCompleted) {
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = PathfinderBlue.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "🏆 Челлендж завершён!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (challenge.rewards.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Призы: " + challenge.rewards.joinToString(", "),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        if (isCompleted && !isRewardClaimed) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.claimReward() },
                                enabled = !state.isUpdatingProgress,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Получить приз")
                            }
                        } else if (isRewardClaimed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Приз получен",
                                style = MaterialTheme.typography.bodySmall,
                                color = PathfinderBlue
                            )
                        }
                    }
                }"""),
], skip_if_marker="fun claimReward // marker")


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Проверка:")
print("  1. Teen → Челленджи → открыть любой активный")
print("  2. 'Присоединиться' → 'Обновить прогресс (+1)' несколько раз")
print("  3. Когда progress == goal появится '🏆 Челлендж завершён!'")
print("  4. Появится кнопка 'Получить приз'")
print("  5. После нажатия — снекбар '🏆 Призы получены: ...'")
print("  6. В достижениях появятся новые ачивки (если их ID были в rewards)")