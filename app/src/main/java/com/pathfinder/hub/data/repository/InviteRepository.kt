package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.InviteDao
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class InviteRepository @Inject constructor(private val dao: InviteDao) {

    suspend fun getByCode(code: String): InviteEntity? = dao.getByCode(code)

    fun observeByClub(clubId: String): Flow<List<InviteEntity>> = dao.observeByClub(clubId)

    suspend fun upsert(invite: InviteEntity) = dao.upsert(invite)

    suspend fun markUsed(code: String, status: String, userId: String, at: Long) =
        dao.markUsed(code, status, userId, at)

    suspend fun expireOld(now: Long) = dao.expireOld(now)

    /**
     * Создать новый инвайт-код.
     * Используется директором клуба.
     */
    suspend fun createInvite(
        clubId: String,
        role: String,
        createdBy: String,
        lifetimeDays: Int = 7
    ): InviteEntity {
        val now = Date()
        val expiresAt = Date(now.time + lifetimeDays * 24L * 3600 * 1000)
        val invite = InviteEntity(
            id = UUID.randomUUID().toString(),
            code = generateCode(),
            clubId = clubId,
            role = role,
            createdBy = createdBy,
            createdAt = now,
            expiresAt = expiresAt,
            status = "active",
            usedBy = null,
            usedAt = null
        )
        dao.upsert(invite)
        return invite
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generateCode(): String =
            (1..8).map { ALPHABET.random() }
                .joinToString("")
                .chunked(4)
                .joinToString("-")
    }
}