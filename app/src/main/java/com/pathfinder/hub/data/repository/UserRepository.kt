package com.pathfinder.hub.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.pathfinder.hub.data.local.dao.UserDao
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.remote.FirestoreUserDataSource
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val dao: UserDao,
    private val syncQueueManager: SyncQueueManager,
    private val firestore: FirestoreUserDataSource,
    private val db: com.pathfinder.hub.data.local.AppDatabase,
    private val userClubDao: com.pathfinder.hub.data.local.dao.UserClubDao,
) {

    companion object {
        private const val TAG = "UserRepository"

        /**
         * Единая нормализация email.
         * Email — регистронезависимый. Всегда храним и сравниваем в lowercase.
         */
        fun normalizeEmail(email: String): String = email.trim().lowercase()
    }

    // ---------- Чтение ----------
    fun observeUser(id: String): Flow<UserEntity?> = dao.observeUser(id)

    /**
     * Полное чтение: Room, при отсутствии — Firestore.
     * Использовать для UI, когда интернет может быть.
     */
    suspend fun getUser(id: String): UserEntity? {
        val local = dao.getUser(id)
        if (local != null) return local

        val remote = firestore.getUser(id)
        if (remote != null) {
            dao.upsert(remote)
        }
        return remote
    }

    /**
     * Только Room, без Firestore.
     * Использовать для автологина и офлайн-проверок.
     */
    suspend fun getUserLocalOnly(id: String): UserEntity? = dao.getUser(id)

    suspend fun getUserByEmail(email: String): UserEntity? =
        dao.getUserByEmail(normalizeEmail(email))

    /** Пакетный резолв имён: один запрос вместо N. */
    suspend fun getUsersByIds(ids: List<String>): List<UserEntity> =
        dao.getUsersByIds(ids)

    fun observeClubMembers(clubId: String): Flow<List<UserEntity>> =
        dao.observeClubMembers(clubId)

    fun observeClubMembersByRole(clubId: String, role: String): Flow<List<UserEntity>> =
        dao.observeClubMembersByRole(clubId, role)

    /**
     * Список участников клуба с одной из указанных ролей.
     * Для «Сотрудников»: director + instructor + secretary.
     */
    fun observeClubMembersByRoles(clubId: String, roles: List<String>): Flow<List<UserEntity>> =
        dao.observeClubMembersByRoles(clubId, roles)

    fun observeConferenceMembers(conferenceId: String): Flow<List<UserEntity>> =
        dao.observeConferenceUsers(conferenceId)

    fun observeConferenceMembersByRole(conferenceId: String, role: String): Flow<List<UserEntity>> =
        dao.observeConferenceMembersByRole(conferenceId, role)

    // ---------- Запись ----------
    suspend fun upsert(u: UserEntity) {
        val normalized = u.copy(email = normalizeEmail(u.email))
        db.withTransaction {
            dao.upsert(normalized)
            syncUserClubs(normalized)
        }
        firestore.saveUser(normalized)
        syncQueueManager.enqueue("user", normalized.id, "CREATE", normalized)
    }

    suspend fun upsertAll(us: List<UserEntity>) {
        val normalized = us.map { it.copy(email = normalizeEmail(it.email)) }
        db.withTransaction {
            dao.upsertAll(normalized)
            normalized.forEach { syncUserClubs(it) }
        }
        normalized.forEach {
            firestore.saveUser(it)
            syncQueueManager.enqueue("user", it.id, "CREATE", it)
        }
    }

    suspend fun update(u: UserEntity) {
        val normalized = u.copy(email = normalizeEmail(u.email))
        db.withTransaction {
            dao.update(normalized)
            syncUserClubs(normalized)
        }
        firestore.updateUser(normalized)
        syncQueueManager.enqueue("user", normalized.id, "UPDATE", normalized)
    }

    suspend fun delete(u: UserEntity) {
        dao.delete(u)
        syncQueueManager.enqueue("user", u.id, "DELETE", u)
    }

    /**
     * Обновляет только флаг emailVerified.
     * Вызывается после входа, когда пользователь подтвердил почту.
     *
     * Пишем в Room локально и синхронизируем с Firestore.
     * НЕ ставим в SyncQueue — это не критичное изменение,
     * и оно не должно конфликтовать с другими апдейтами User.
     */
    suspend fun updateEmailVerified(id: String, verified: Boolean) {
        val current = dao.getUser(id) ?: return
        if (current.emailVerified == verified) return  // нечего менять

        val updated = current.copy(emailVerified = verified)
        dao.update(updated)
        firestore.updateUser(updated)
        Log.d(TAG, "updateEmailVerified: $id → $verified")
    }

    suspend fun updateAccessLevel(id: String, l: String) {
        dao.updateAccessLevel(id, l)
        dao.getUser(id)?.let {
            firestore.updateUser(it)
            syncQueueManager.enqueue("user", id, "UPDATE", it)
        }
    }

    suspend fun completeOnboarding(id: String) {
        dao.completeOnboarding(id)
        dao.getUser(id)?.let {
            firestore.updateUser(it)
            syncQueueManager.enqueue("user", id, "UPDATE", it)
        }
    }

    suspend fun requestDeletion(id: String, s: String, at: Long) {
        dao.requestDeletion(id, s, at)
        dao.getUser(id)?.let {
            firestore.updateUser(it)
            syncQueueManager.enqueue("user", id, "UPDATE", it)
        }
    }

    // ---------- Мульти-клуб ----------

    /**
     * Сменить активный клуб.
     * clubId остаётся клубом по умолчанию для старых мест.
     */
    suspend fun setActiveClub(userId: String, clubId: String) {
        val current = dao.getUser(userId) ?: return
        if (current.clubId == clubId) return
        val updated = current.copy(clubId = clubId)
        dao.update(updated)
        firestore.updateUser(updated)
        syncQueueManager.enqueue("user", userId, "UPDATE", updated)
        Log.d(TAG, "setActiveClub: $userId → $clubId")
    }

    /**
     * Добавить клуб пользователю.
     * Первый добавленный становится активным.
     */
    suspend fun addClubToUser(userId: String, clubId: String) {
        var current = dao.getUser(userId) ?: return
        // Если локально clubIds пусто — сначала подтянуть remote,
        // чтобы не потерять старые клубы (после wipe/логина).
        if (current.clubIds.isEmpty()) {
            try {
                val remote = firestore.getUser(userId)
                if (remote != null && remote.clubIds.isNotEmpty()) {
                    current = current.copy(
                        clubIds = remote.clubIds,
                        clubId = current.clubId ?: remote.clubId,
                    )
                    dao.update(current)
                    Log.d(TAG, "addClubToUser: подтянул remote clubIds=${remote.clubIds}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "addClubToUser: remote pull failed ${e.message}")
            }
        }
        val newList = buildSet {
            current.clubId?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(current.clubIds.filter { it.isNotBlank() })
            add(clubId)
        }.toList()
        val updated = current.copy(
            clubIds = newList,
            clubId = current.clubId ?: clubId,
        )
        db.withTransaction {
            dao.update(updated)
            userClubDao.insert(
                com.pathfinder.hub.data.local.entity.core.UserClubEntity(userId, clubId)
            )
        }
        firestore.updateUser(updated)
        syncQueueManager.enqueue("user", userId, "UPDATE", updated)
        Log.d(TAG, "addClubToUser: $userId += $clubId (total=${newList.size})")
    }

    /**
     * Убрать клуб у пользователя.
     * Если удаляем активный — активным становится первый из оставшихся.
     */
    suspend fun removeClubFromUser(userId: String, clubId: String) {
        val current = dao.getUser(userId) ?: return
        val newList = current.clubIds.filter { it != clubId }
        val newActive = when {
            current.clubId != clubId -> current.clubId
            newList.isNotEmpty() -> newList.first()
            else -> null
        }
        val updated = current.copy(clubIds = newList, clubId = newActive)
        db.withTransaction {
            dao.update(updated)
            userClubDao.delete(userId, clubId)
        }
        firestore.updateUser(updated)
        syncQueueManager.enqueue("user", userId, "UPDATE", updated)
        Log.d(TAG, "removeClubFromUser: $userId -= $clubId (total=${newList.size})")
    }

    // ---------- Передача клуба ----------

    /**
     * Передача владения клубом от старого owner'а новому.
     * Порядок: сначала новый получает owner, потом старый теряет.
     * Старый ПОЛНОСТЬЮ выпадает из клуба (clubIds -= clubId).
     */
    suspend fun transferClubOwnership(
        oldOwnerId: String,
        newOwnerId: String,
        clubId: String,
    ): Result<Unit> {
        return try {
        if (oldOwnerId == newOwnerId) {
            return Result.failure(IllegalArgumentException("Нельзя передать клуб самому себе"))
        }
        val oldOwner = dao.getUser(oldOwnerId)
            ?: return Result.failure(IllegalStateException("Старый владелец не найден"))
        val newOwner = dao.getUser(newOwnerId)
            ?: return Result.failure(IllegalStateException("Новый владелец не найден"))

        if (!oldOwner.isOwnerClub) {
            return Result.failure(IllegalStateException("Вы не являетесь владельцем клуба"))
        }
        if (clubId !in oldOwner.clubIds) {
            return Result.failure(IllegalStateException("Клуб не принадлежит вам"))
        }
        if (clubId !in newOwner.clubIds) {
            return Result.failure(IllegalStateException("Получатель не является сотрудником этого клуба"))
        }

        // Шаг 1: новый становится owner'ом
        val newList = (newOwner.clubIds + clubId).distinct()
        val newUpdated = newOwner.copy(
            role = "director",
            isOwnerClub = true,
            clubIds = newList,
            clubId = clubId,
        )
        update(newUpdated)

        // Шаг 2: старый теряет owner и выпадает из клуба
        val oldList = oldOwner.clubIds.filter { it != clubId }
        val oldActive = if (oldOwner.clubId == clubId) oldList.firstOrNull() else oldOwner.clubId
        val oldUpdated = oldOwner.copy(
            isOwnerClub = false,
            clubIds = oldList,
            clubId = oldActive,
        )
        update(oldUpdated)

        Log.d(TAG, "transferClubOwnership: $clubId: $oldOwnerId -> $newOwnerId (old clubs left=${oldList.size})")
        Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "transferClubOwnership FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Передача клуба по email существующего пользователя.
     * Если user уже в клубе — transfer сразу.
     * Если не в клубе — добавляем в clubIds, потом transfer.
     */
    suspend fun transferClubOwnershipByEmail(
        oldOwnerId: String,
        email: String,
        clubId: String,
    ): Result<Unit> {
        return try {
            val normalized = normalizeEmail(email)
            if (normalized.isBlank() || !normalized.contains("@")) {
                return Result.failure(IllegalArgumentException("Некорректный email"))
            }
            val target = dao.getUserByEmail(normalized)
                ?: return Result.failure(IllegalStateException("Пользователь с таким email не найден. Используйте приглашение."))
            if (target.id == oldOwnerId) {
                return Result.failure(IllegalArgumentException("Нельзя передать клуб самому себе"))
            }

            // Если не в клубе — сначала добавляем как сотрудника
            if (clubId !in target.clubIds) {
                val nowList = buildSet {
                    target.clubId?.takeIf { it.isNotBlank() }?.let { add(it) }
                    addAll(target.clubIds.filter { it.isNotBlank() })
                    add(clubId)
                }.toList()
                val prepared = target.copy(clubIds = nowList)
                dao.update(prepared)
                firestore.updateUser(prepared)
                syncQueueManager.enqueue("user", prepared.id, "UPDATE", prepared)
            }

            transferClubOwnership(oldOwnerId, target.id, clubId)
        } catch (e: Exception) {
            Log.e(TAG, "transferClubOwnershipByEmail FAILED", e)
            Result.failure(e)
        }
    }

    // ---------- user_clubs: синхронизация ----------

    /**
     * Полная замена связей user_clubs для юзера.
     * Источник правды — clubId + clubIds.
     */
    private suspend fun syncUserClubs(u: UserEntity) {
        userClubDao.deleteAllForUser(u.id)
        val ids = buildSet {
            u.clubId?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(u.clubIds.filter { it.isNotBlank() })
        }
        if (ids.isEmpty()) return
        userClubDao.insertAll(
            ids.map { com.pathfinder.hub.data.local.entity.core.UserClubEntity(u.id, it) }
        )
    }
}
