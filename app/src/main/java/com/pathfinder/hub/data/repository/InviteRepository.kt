package com.pathfinder.hub.data.repository

import android.util.Log
import com.pathfinder.hub.data.local.dao.InviteDao
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import com.pathfinder.hub.data.remote.FirestoreInviteDataSource
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InviteRepository @Inject constructor(
    private val dao: InviteDao,
    private val remote: FirestoreInviteDataSource
) {

    companion object {
        private const val TAG = "InviteRepository"
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generateCode(): String =
            (1..8).map { ALPHABET.random() }
                .joinToString("")
                .chunked(4)
                .joinToString("-")
    }

    // ==================== ЧТЕНИЕ ====================

    suspend fun getByCode(code: String): InviteEntity? {
        val remoteInvite = remote.getInviteByCode(code)
        if (remoteInvite != null) {
            try {
                dao.upsert(remoteInvite)
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось закэшировать инвайт $code", e)
            }
            return remoteInvite
        }

        val localInvite = dao.getByCode(code)
        if (localInvite != null) {
            Log.d(TAG, "getByCode($code): отдаём из Room (офлайн)")
        }
        return localInvite
    }

    fun observeByClub(clubId: String): Flow<List<InviteEntity>> = dao.observeByClub(clubId)

    suspend fun upsert(invite: InviteEntity) {
        dao.upsert(invite)
        remote.createInvite(invite)
    }

    // ==================== СОЗДАНИЕ ====================

    suspend fun createInvite(
        clubId: String,
        role: String,
        createdBy: String,
        lifetimeDays: Int = 7,
        permissions: List<String> = emptyList(),
        conferenceId: String? = null,
    ): InviteEntity {
        val now = Date()
        val expiresAt = Date(now.time + lifetimeDays * 24L * 3600 * 1000)
        val invite = InviteEntity(
            id = UUID.randomUUID().toString(),
            code = generateCode(),
            clubId = clubId,
            conferenceId = conferenceId,
            role = role,
            permissions = permissions,
            createdBy = createdBy,
            createdAt = now,
            expiresAt = expiresAt,
            status = "active",
            usedBy = null,
            usedAt = null
        )

        val ok = remote.createInvite(invite)
        if (!ok) {
            Log.w(TAG, "createInvite: Firestore недоступен, инвайт только в Room")
        }

        dao.upsert(invite)
        return invite
    }

    // ==================== ПЕРЕДАЧА КЛУБА ЧЕРЕЗ ИНВАЙТ ====================

    /**
     * Создаёт transfer-invite (передача владения клубом новому директору).
     * Перед созданием отзывает все активные transfer-invites этого клуба
     * (правило: один активный transfer-invite за раз).
     */
    suspend fun createTransferInvite(
        clubId: String,
        createdBy: String,
        lifetimeDays: Int = 7,
    ): InviteEntity {
        revokeActiveTransferInvites(clubId)

        val now = Date()
        val expiresAt = Date(now.time + lifetimeDays * 24L * 3600 * 1000)
        val invite = InviteEntity(
            id = UUID.randomUUID().toString(),
            code = generateCode(),
            clubId = clubId,
            conferenceId = null,
            role = "director",
            permissions = emptyList(),
            createdBy = createdBy,
            createdAt = now,
            expiresAt = expiresAt,
            status = "active",
            usedBy = null,
            usedAt = null,
            transferOwnership = true,
        )
        val ok = remote.createInvite(invite)
        if (!ok) {
            Log.w(TAG, "createTransferInvite: Firestore недоступен, только Room")
        }
        dao.upsert(invite)
        return invite
    }

    /** Отзывает все активные transfer-invites клуба. */
    suspend fun revokeActiveTransferInvites(clubId: String) {
        val all = dao.getByClubOnce(clubId)
        all.filter { it.transferOwnership && it.status == "active" }.forEach { inv ->
            val revoked = inv.copy(status = "revoked")
            remote.createInvite(revoked)  // перезаписываем документ
            dao.upsert(revoked)
        }
    }

    // ==================== АТОМАРНЫЙ CLAIM ====================

    suspend fun reserveInvite(code: String): Result<InviteEntity> {
        val result = remote.reserveInvite(code)

        result.onSuccess { invite ->
            try {
                dao.upsert(invite)
            } catch (e: Exception) {
                Log.w(TAG, "reserveInvite: не удалось закэшировать $code", e)
            }
        }

        return result
    }

    suspend fun confirmInvite(code: String, uid: String): Result<InviteEntity> {
        val result = remote.confirmInvite(code, uid)

        result.onSuccess { invite ->
            try {
                dao.upsert(invite)
            } catch (e: Exception) {
                Log.w(TAG, "confirmInvite: не удалось закэшировать $code", e)
            }
        }

        return result
    }

    suspend fun releaseInvite(code: String) {
        remote.releaseInvite(code)
        try {
            val local = dao.getByCode(code)
            if (local != null && local.status == "claiming") {
                dao.upsert(local.copy(status = "active", usedBy = null, usedAt = null))
            }
        } catch (e: Exception) {
            Log.w(TAG, "releaseInvite: локальное обновление не удалось $code", e)
        }
    }

    // ==================== СТАРЫЕ МЕТОДЫ ====================

    suspend fun markUsed(code: String, status: String, userId: String, at: Long) =
        dao.markUsed(code, status, userId, at)

    suspend fun expireOld(now: Long) = dao.expireOld(now)
}