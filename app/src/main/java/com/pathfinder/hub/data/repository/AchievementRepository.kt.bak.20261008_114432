package com.pathfinder.hub.data.repository
import com.pathfinder.hub.data.local.dao.AchievementDao
import com.pathfinder.hub.data.local.entity.gamification.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AchievementRepository @Inject constructor(private val dao: AchievementDao) {
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
    suspend fun upsertHomework(h: HomeworkEntity) = dao.upsertHomework(h)
    fun observeActivityLevel(u: String): Flow<ActivityLevelEntity?> = dao.observeActivityLevel(u)
    suspend fun upsertActivityLevel(a: ActivityLevelEntity) = dao.upsertActivityLevel(a)
    fun observeReputation(u: String): Flow<ReputationEntity?> = dao.observeReputation(u)
    suspend fun upsertReputation(r: ReputationEntity) = dao.upsertReputation(r)
    fun observeCertificates(u: String): Flow<List<CertificateEntity>> = dao.observeCertificates(u)
    suspend fun upsertCertificate(c: CertificateEntity) = dao.upsertCertificate(c)
    suspend fun upsertShareCard(s: ShareCardEntity) = dao.upsertShareCard(s)
    suspend fun getShareCard(t: String, id: String): ShareCardEntity? = dao.getShareCard(t, id)
}
