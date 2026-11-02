package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.CrossClubDao
import com.pathfinder.hub.data.local.entity.crossclub.CrossClubInviteCodeEntity
import com.pathfinder.hub.data.local.entity.crossclub.CrossClubInviteEntity
import com.pathfinder.hub.data.local.entity.crossclub.CrossClubInviteUsageEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first


/**
 * Cross-club приглашения.
 *
 * Лимиты:
 *   - Директор A: не более 10 pending приглашений (проверяется в VM).
 *   - Teen A: не более 3 кодов в день (MAX_CODES_PER_DAY).
 */
@Singleton
class CrossClubRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: CrossClubDao,
) {

    companion object {
        const val MAX_PENDING_INVITES_PER_CLUB = 10
        const val MAX_CODES_PER_DAY = 3
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generateCode(): String =
            (1..8).map { ALPHABET.random() }
                .joinToString("")
                .chunked(4)
                .joinToString("-")
    }

    // ---------- Явные приглашения ----------

    fun observeHostInvites(clubId: String): Flow<List<CrossClubInviteEntity>> =
        dao.observeByHost(clubId)

    fun observeInviteeInvites(clubId: String): Flow<List<CrossClubInviteEntity>> =
        dao.observeByInvitee(clubId)

    suspend fun getInvite(id: String) = dao.getInvite(id)

    suspend fun countPendingByHost(clubId: String) = dao.countPendingByHost(clubId)

    suspend fun createInvite(
        hostClubId: String,
        inviteeClubId: String,
        targetType: String,
        targetId: String,
        createdBy: String,
        lifetimeDays: Int = 30,
    ): Result<CrossClubInviteEntity> {
        if (hostClubId == inviteeClubId) {
            return Result.failure(IllegalArgumentException("Нельзя пригласить свой же клуб"))
        }
        val pending = dao.countPendingByHost(hostClubId)
        if (pending >= MAX_PENDING_INVITES_PER_CLUB) {
            return Result.failure(IllegalStateException(
                "Лимит активных приглашений: $MAX_PENDING_INVITES_PER_CLUB"
            ))
        }
        return try {
            val now = Date()
            val expires = Date(now.time + lifetimeDays.toLong() * 24 * 3600 * 1000)
            val entity = CrossClubInviteEntity(
                id = UUID.randomUUID().toString(),
                hostClubId = hostClubId,
                inviteeClubId = inviteeClubId,
                targetType = targetType,
                targetId = targetId,
                createdBy = createdBy,
                createdAt = now,
                expiresAt = expires,
                status = "pending",
                pendingSync = true,
            )
            db.withTransaction { dao.upsertInvite(entity) }
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revokeInvite(id: String) {
        db.withTransaction { dao.updateInviteStatus(id, "expired") }
    }

    suspend fun acceptInvite(id: String) {
        db.withTransaction { dao.updateInviteStatus(id, "accepted") }
    }

    suspend fun declineInvite(id: String) {
        db.withTransaction { dao.updateInviteStatus(id, "declined") }
    }

    // ---------- Коды teen → teen ----------

    fun observeCodesByCreator(userId: String): Flow<List<CrossClubInviteCodeEntity>> =
        dao.observeCodesByCreator(userId)

    fun observeCodesByClub(clubId: String): Flow<List<CrossClubInviteCodeEntity>> =
        dao.observeCodesByClub(clubId)

    suspend fun getCode(code: String) = dao.getCode(code)

    suspend fun getCodeById(id: String) = dao.getCodeById(id)

    /**
     * Проверка дневного лимита для teen.
     */
    suspend fun codesCreatedToday(userId: String): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return dao.countCodesCreatedSince(userId, cal.timeInMillis)
    }

    suspend fun createCode(
        hostClubId: String,
        targetType: String,
        targetId: String,
        createdBy: String,
        lifetimeDays: Int? = 30,
    ): Result<CrossClubInviteCodeEntity> {
        val today = dao.countCodesCreatedSince(
            createdBy,
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
        if (today >= MAX_CODES_PER_DAY) {
            return Result.failure(IllegalStateException(
                "Не более $MAX_CODES_PER_DAY кодов в день"
            ))
        }
        return try {
            val now = Date()
            val expires = lifetimeDays?.let { Date(now.time + it.toLong() * 24 * 3600 * 1000) }
            val entity = CrossClubInviteCodeEntity(
                id = UUID.randomUUID().toString(),
                code = generateCode(),
                hostClubId = hostClubId,
                targetType = targetType,
                targetId = targetId,
                createdBy = createdBy,
                maxUses = 50,
                usedCount = 0,
                expiresAt = expires,
                status = "active",
                createdAt = now,
                pendingSync = true,
            )
            db.withTransaction { dao.upsertCode(entity) }
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revokeCode(id: String) {
        db.withTransaction { dao.updateCodeStatus(id, "revoked") }
    }

    /**
     * Активация кода teen'ом из другого клуба.
     * Возвращает Result<CrossClubInviteCodeEntity> при успехе.
     */
    suspend fun redeemCode(
        code: String,
        userId: String,
        userClubId: String,
    ): Result<CrossClubInviteCodeEntity> {
        val entity = dao.getCode(code)
            ?: return Result.failure(IllegalArgumentException("Код не найден"))

        if (entity.status != "active") {
            return Result.failure(IllegalStateException("Код неактивен"))
        }
        if (entity.expiresAt != null && entity.expiresAt.before(Date())) {
            return Result.failure(IllegalStateException("Срок действия кода истёк"))
        }
        if (entity.maxUses > 0 && entity.usedCount >= entity.maxUses) {
            return Result.failure(IllegalStateException("Код исчерпан"))
        }
        if (entity.hostClubId == userClubId) {
            return Result.failure(IllegalStateException("Это ваш собственный клуб"))
        }
        if (dao.countUsagesByUser(entity.id, userId) > 0) {
            return Result.failure(IllegalStateException("Вы уже использовали этот код"))
        }

        return try {
            db.withTransaction {
                dao.incrementCodeUse(entity.id)
                dao.upsertUsage(
                    CrossClubInviteUsageEntity(
                        id = UUID.randomUUID().toString(),
                        inviteCodeId = entity.id,
                        userId = userId,
                        userClubId = userClubId,
                        usedAt = Date(),
                    )
                )
            }
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeUsages(codeId: String): Flow<List<CrossClubInviteUsageEntity>> =
        dao.observeUsagesByCode(codeId)

    fun observeMyUsages(userId: String): Flow<List<CrossClubInviteUsageEntity>> =
        dao.observeUsagesByUser(userId)

    /**
     * Возвращает пары (usage, code) для всех вступлений teen'а.
     * Из code.targetId можно получить eventId.
     */
    suspend fun getJoinedCodes(userId: String): List<Pair<CrossClubInviteUsageEntity, CrossClubInviteCodeEntity>> {
        val usages = dao.observeUsagesByUser(userId).first()
        return usages.mapNotNull { u ->
            val code = dao.getCodeById(u.inviteCodeId)
            code?.let { u to it }
        }
    }
}
