package com.pathfinder.hub.data.repository
import com.pathfinder.hub.data.local.dao.AchievementDao
import com.pathfinder.hub.data.remote.FirestoreCertificateDataSource
import com.pathfinder.hub.data.local.entity.gamification.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AchievementRepository @Inject constructor(
    private val dao: AchievementDao,
    private val certFirestore: FirestoreCertificateDataSource,
) {
    fun observeAll(): Flow<List<AchievementEntity>> = dao.observeAll()
    suspend fun upsertAll(items: List<AchievementEntity>) = dao.upsertAll(items)
    fun observeProgress(u: String): Flow<List<AchievementProgressEntity>> = dao.observeProgress(u)
    fun observeEarned(u: String): Flow<List<AchievementProgressEntity>> = dao.observeEarned(u)
    suspend fun upsertProgress(p: AchievementProgressEntity) = dao.upsertProgress(p)
    suspend fun revoke(u: String, a: String, at: Long, b: String, r: String) = dao.revoke(u, a, at, b, r)
    fun observeUniform(u: String): Flow<DigitalUniformEntity?> = dao.observeUniform(u)
    suspend fun upsertUniform(u: DigitalUniformEntity) = dao.upsertUniform(u)
    fun observeActiveChallenges(c: String): Flow<List<ChallengeEntity>> = dao.observeActiveChallenges(c)
    suspend fun upsertChallenge(c: ChallengeEntity) = dao.upsertChallenge(c)
    fun observeChallengeProgress(id: String): Flow<List<ChallengeProgressEntity>> = dao.observeChallengeProgress(id)
    suspend fun upsertChallengeProgress(p: ChallengeProgressEntity) = dao.upsertChallengeProgress(p)
    fun observeTeamProgress(id: String): Flow<TeamChallengeProgressEntity?> = dao.observeTeamProgress(id)
    suspend fun upsertTeamProgress(p: TeamChallengeProgressEntity) = dao.upsertTeamProgress(p)
    fun observeHomework(c: String): Flow<List<HomeworkEntity>> = dao.observeHomework(c)

    // ---------- Архивация домашек ----------
    fun observeActiveHomework(childId: String): Flow<List<HomeworkEntity>> =
        dao.observeActiveHomework(childId)

    fun observeArchivedHomework(childId: String): Flow<List<HomeworkEntity>> =
        dao.observeArchivedHomework(childId)

    suspend fun archiveHomework(id: String) {
        val now = java.util.Date()
        val purgeAfter = java.util.Date(now.time + 90L * 24 * 3600 * 1000)
        dao.archiveHomework(id, now.time, purgeAfter.time)
    }

    suspend fun restoreHomework(id: String) {
        dao.restoreHomework(id)
    }

    suspend fun purgeExpiredHomework(now: Long = System.currentTimeMillis()): Int {
        return dao.purgeExpiredHomework(now)
    }
    suspend fun upsertHomework(h: HomeworkEntity) = dao.upsertHomework(h)

    fun observePendingHomeworkByClub(clubId: String): Flow<List<HomeworkEntity>> =
        dao.observePendingHomeworkByClub(clubId)

    /** Все активные домашки клуба — для редактора тестов. */
    fun observeActiveHomeworkByClub(clubId: String): Flow<List<HomeworkEntity>> =
        dao.observeActiveHomeworkByClub(clubId)

    /** Все архивные домашки клуба — для экрана «Архив». */
    fun observeArchivedHomeworkByClub(clubId: String): Flow<List<HomeworkEntity>> =
        dao.observeArchivedHomeworkByClub(clubId)

    suspend fun approveHomework(h: HomeworkEntity) {
        dao.upsertHomework(
            h.copy(
                status = "approved",
                approvedAt = java.util.Date(),
                pendingSync = true,
            )
        )

        // S4: начисление по типу награды.
        when (h.rewardType) {
            "points" -> {
                if (h.rewardPoints > 0) {
                    val current = dao.observeReputation(h.childId).first()
                    val updated = current?.copy(
                        personal = current.personal + h.rewardPoints,
                        updatedAt = java.util.Date(),
                    ) ?: ReputationEntity(
                        userId = h.childId,
                        clubId = h.clubId,
                        personal = h.rewardPoints,
                        team = 0,
                        updatedAt = java.util.Date(),
                    )
                    dao.upsertReputation(updated)
                }
            }
            "achievement" -> {
                val achId = h.rewardAchievementId
                if (!achId.isNullOrBlank()) {
                    dao.upsertProgress(
                        AchievementProgressEntity(
                            userId = h.childId,
                            achievementId = achId,
                            status = "earned",
                            earnedAt = java.util.Date(),
                            revokedAt = null,
                            revokedBy = null,
                            revokeReason = null,
                            pendingSync = false,
                        )
                    )
                }
            }
            // "text" — ничего
        }
    }

    suspend fun rejectHomework(h: HomeworkEntity) =
        dao.upsertHomework(h.copy(
            status = "rejected",
            approvedAt = null,
            pendingSync = true,
        ))
    fun observeActivityLevel(u: String): Flow<ActivityLevelEntity?> = dao.observeActivityLevel(u)
    suspend fun upsertActivityLevel(a: ActivityLevelEntity) = dao.upsertActivityLevel(a)
    fun observeReputation(u: String): Flow<ReputationEntity?> = dao.observeReputation(u)
    suspend fun upsertReputation(r: ReputationEntity) = dao.upsertReputation(r)
    fun observeCertificates(u: String): Flow<List<CertificateEntity>> = dao.observeCertificates(u)
    suspend fun upsertCertificate(c: CertificateEntity) = dao.upsertCertificate(c)

    /**
     * Сохраняет сертификат локально + в Firestore.
     * Используется после upload PDF в Yandex.
     */
    suspend fun upsertCertificateWithSync(c: CertificateEntity) {
        dao.upsertCertificate(c)
        certFirestore.upsert(c)
    }
    suspend fun upsertShareCard(s: ShareCardEntity) = dao.upsertShareCard(s)
    suspend fun getShareCard(t: String, id: String): ShareCardEntity? = dao.getShareCard(t, id)
}
