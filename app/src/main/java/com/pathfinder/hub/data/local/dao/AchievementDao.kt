package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.gamification.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AchievementEntity>)
    @Query("SELECT * FROM achievement_progress WHERE userId = :userId")
    fun observeProgress(userId: String): Flow<List<AchievementProgressEntity>>
    @Query("SELECT * FROM achievement_progress WHERE userId = :userId AND status = 'earned'")
    fun observeEarned(userId: String): Flow<List<AchievementProgressEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: AchievementProgressEntity)
    @Query("""UPDATE achievement_progress SET status = 'revoked', revokedAt = :at,
        revokedBy = :by, revokeReason = :reason
        WHERE userId = :userId AND achievementId = :achId""")
    suspend fun revoke(userId: String, achId: String, at: Long, by: String, reason: String)
    @Query("SELECT * FROM digital_uniforms WHERE userId = :userId")
    fun observeUniform(userId: String): Flow<DigitalUniformEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUniform(u: DigitalUniformEntity)
    @Query("SELECT * FROM challenges WHERE clubId = :clubId AND status = 'active'")
    fun observeActiveChallenges(clubId: String): Flow<List<ChallengeEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChallenge(c: ChallengeEntity)
    @Query("SELECT * FROM challenge_progress WHERE challengeId = :challengeId")
    fun observeChallengeProgress(challengeId: String): Flow<List<ChallengeProgressEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChallengeProgress(p: ChallengeProgressEntity)
    @Query("SELECT * FROM team_challenge_progress WHERE challengeId = :challengeId")
    fun observeTeamProgress(challengeId: String): Flow<TeamChallengeProgressEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTeamProgress(p: TeamChallengeProgressEntity)
    @Query("SELECT * FROM homework WHERE childId = :childId")
    fun observeHomework(childId: String): Flow<List<HomeworkEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHomework(h: HomeworkEntity)
    @Query("SELECT * FROM activity_levels WHERE userId = :userId")
    fun observeActivityLevel(userId: String): Flow<ActivityLevelEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivityLevel(a: ActivityLevelEntity)
    @Query("SELECT * FROM reputations WHERE userId = :userId")
    fun observeReputation(userId: String): Flow<ReputationEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReputation(r: ReputationEntity)
    @Query("SELECT * FROM certificates WHERE userId = :userId ORDER BY issuedAt DESC")
    fun observeCertificates(userId: String): Flow<List<CertificateEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCertificate(c: CertificateEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShareCard(s: ShareCardEntity)
    @Query("SELECT * FROM share_cards WHERE type = :type AND targetId = :targetId")
    suspend fun getShareCard(type: String, targetId: String): ShareCardEntity?
}
