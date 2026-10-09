package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для инвайтов.
 *
 * Ключ документа: invites/{code} — по нему ищет пользователь при регистрации.
 *
 * Атомарность: reserveInvite использует runTransaction, поэтому два
 * одновременных запроса на один и тот же код дадут ровно один успех.
 *
 * Жизненный цикл инвайта:
 *   active    → можно зарезервировать
 *   claiming  → зарезервирован на 5 минут (идёт регистрация)
 *   used      → успешно использован
 *   expired   → истёк по времени
 */
@Singleton
class FirestoreInviteDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val invitesCollection = firestore.collection("invites")

    companion object {
        private const val TAG = "FirestoreInviteDS"
        private const val CLAIM_TTL_MS = 5 * 60 * 1000L  // 5 минут
        const val STATUS_ACTIVE = "active"
        const val STATUS_CLAIMING = "claiming"
        const val STATUS_USED = "used"
        const val STATUS_EXPIRED = "expired"
    }

    // ==================== ЧТЕНИЕ ====================

    suspend fun getInviteByCode(code: String): InviteEntity? {
        return try {
            val snapshot = invitesCollection.document(code).get().await()
            if (!snapshot.exists()) {
                Log.w(TAG, "invites/$code не найден")
                return null
            }
            val data = snapshot.data ?: return null
            mapToEntity(data)
        } catch (e: Exception) {
            Log.e(TAG, "getInviteByCode($code) FAILED", e)
            null
        }
    }

    // ==================== СОЗДАНИЕ ====================

    suspend fun createInvite(invite: InviteEntity): Boolean = try {
        invitesCollection.document(invite.code)
            .set(mapFromEntity(invite))
            .await()
        Log.d(TAG, "createInvite OK: ${invite.code}")
        true
    } catch (e: Exception) {
        Log.e(TAG, "createInvite FAILED: ${invite.code}", e)
        false
    }

    // ==================== РЕЗЕРВИРОВАНИЕ (ФАЗА 1) ====================

    /**
     * Атомарно резервирует инвайт: status active → claiming.
     *
     * Возвращает:
     *   - Result.success(invite) — инвайт зарезервирован, можно создавать Auth
     *   - Result.failure(InviteNotActiveException) — инвайт уже занят/использован
     *   - Result.failure(InviteExpiredException) — истёк
     *   - Result.failure(InviteNotFoundException) — не найден
     *
     * Транзакция: все чтения до записи. Firestore сам повторит при конфликте.
     */
    suspend fun reserveInvite(code: String): Result<InviteEntity> {
        return try {
            val result = firestore.runTransaction { transaction ->
                val docRef = invitesCollection.document(code)
                val snapshot = transaction.get(docRef)

                if (!snapshot.exists()) {
                    throw InviteNotFoundException(code)
                }

                val data = snapshot.data ?: throw InviteNotFoundException(code)
                val status = data["status"] as? String ?: STATUS_EXPIRED
                val expiresAt = parseDate(data["expiresAt"])

                // Проверяем срок
                if (expiresAt != null && expiresAt.before(Date())) {
                    transaction.update(docRef, mapOf(
                        "status" to STATUS_EXPIRED
                    ))
                    throw InviteExpiredException(code)
                }

                // Проверяем статус
                when (status) {
                    STATUS_ACTIVE -> { /* можно резервировать */ }
                    STATUS_CLAIMING -> {
                        // Если claim «завис» больше TTL — разрешаем перерезервировать
                        val claimedAt = parseDate(data["claimedAt"])
                        val isStale = claimedAt == null
                                || (System.currentTimeMillis() - claimedAt.time) > CLAIM_TTL_MS
                        if (!isStale) {
                            throw InviteAlreadyClaimedException(code)
                        }
                        Log.w(TAG, "Резервирование $code устарело, перерезервируем")
                    }
                    STATUS_USED -> throw InviteAlreadyUsedException(code)
                    STATUS_EXPIRED -> throw InviteExpiredException(code)
                    else -> throw InviteNotFoundException(code)
                }

                // Обновляем: active → claiming
                val now = Timestamp.now()
                transaction.update(docRef, mapOf(
                    "status" to STATUS_CLAIMING,
                    "claimedAt" to now
                ))

                // Возвращаем обновлённую entity
                mapToEntity(data + mapOf(
                    "status" to STATUS_CLAIMING,
                    "claimedAt" to now
                ))
            }.await()

            Log.d(TAG, "reserveInvite OK: $code")
            Result.success(result)
        } catch (e: InviteNotFoundException) {
            Log.w(TAG, "reserveInvite: $code — не найден")
            Result.failure(e)
        } catch (e: InviteExpiredException) {
            Log.w(TAG, "reserveInvite: $code — истёк")
            Result.failure(e)
        } catch (e: InviteAlreadyUsedException) {
            Log.w(TAG, "reserveInvite: $code — уже использован")
            Result.failure(e)
        } catch (e: InviteAlreadyClaimedException) {
            Log.w(TAG, "reserveInvite: $code — занят другим устройством")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "reserveInvite FAILED: $code", e)
            Result.failure(e)
        }
    }

    // ==================== ПОДТВЕРЖДЕНИЕ (ФАЗА 2) ====================

    /**
     * Атомарно подтверждает использование: status claiming → used,
     * проставляет usedBy и usedAt.
     *
     * Вызывается ПОСЛЕ успешного создания Firebase Auth, когда uid известен.
     */
    suspend fun confirmInvite(code: String, uid: String): Result<InviteEntity> {
        return try {
            val result = firestore.runTransaction { transaction ->
                val docRef = invitesCollection.document(code)
                val snapshot = transaction.get(docRef)

                if (!snapshot.exists()) {
                    throw InviteNotFoundException(code)
                }

                val data = snapshot.data ?: throw InviteNotFoundException(code)
                val status = data["status"] as? String

                if (status != STATUS_CLAIMING) {
                    throw InviteAlreadyUsedException(code)
                }

                val now = Timestamp.now()
                transaction.update(docRef, mapOf(
                    "status" to STATUS_USED,
                    "usedBy" to uid,
                    "usedAt" to now
                ))

                mapToEntity(data + mapOf(
                    "status" to STATUS_USED,
                    "usedBy" to uid,
                    "usedAt" to now
                ))
            }.await()

            Log.d(TAG, "confirmInvite OK: $code → $uid")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "confirmInvite FAILED: $code", e)
            Result.failure(e)
        }
    }

    // ==================== ОТКАТ ====================

    /**
     * Освобождает зарезервированный инвайт: claiming → active.
     * Вызывается, если создание Auth упало.
     *
     * Возвращает true, даже если инвайт уже не в статусе claiming —
     * это не ошибка, значит, кто-то другой уже разобрался.
     */
    suspend fun releaseInvite(code: String): Boolean = try {
        firestore.runTransaction { transaction ->
            val docRef = invitesCollection.document(code)
            val snapshot = transaction.get(docRef)

            if (!snapshot.exists()) return@runTransaction false

            val status = snapshot.data?.get("status") as? String
            if (status == STATUS_CLAIMING) {
                transaction.update(docRef, mapOf(
                    "status" to STATUS_ACTIVE,
                    "claimedAt" to null
                ))
                true
            } else {
                false
            }
        }.await().also {
            Log.d(TAG, "releaseInvite($code): released=$it")
        }
    } catch (e: Exception) {
        Log.e(TAG, "releaseInvite FAILED: $code", e)
        false
    }

    // ==================== МАППИНГ ====================

    private fun mapToEntity(data: Map<String, Any?>): InviteEntity {
        return InviteEntity(
            id = data["id"] as? String ?: "",
            code = data["code"] as? String ?: "",
            clubId = data["clubId"] as? String ?: "",
            conferenceId = data["conferenceId"] as? String,
            role = data["role"] as? String ?: "teen",
            createdBy = data["createdBy"] as? String ?: "",
            createdAt = parseDate(data["createdAt"]) ?: Date(),
            expiresAt = parseDate(data["expiresAt"]) ?: Date(),
            status = data["status"] as? String ?: STATUS_ACTIVE,
            usedBy = data["usedBy"] as? String,
            usedAt = parseDate(data["usedAt"])
        )
    }

    private fun mapFromEntity(invite: InviteEntity): Map<String, Any?> {
        return mapOf(
            "id" to invite.id,
            "code" to invite.code,
            "clubId" to invite.clubId,
            "conferenceId" to invite.conferenceId,
            "role" to invite.role,
            "permissions" to invite.permissions,
            "createdBy" to invite.createdBy,
            "createdAt" to Timestamp(invite.createdAt),
            "expiresAt" to Timestamp(invite.expiresAt),
            "status" to invite.status,
            "usedBy" to invite.usedBy,
            "usedAt" to invite.usedAt?.let { Timestamp(it) }
        )
    }

    /**
     * Парсер дат из Firestore: поддерживает Timestamp, Date, Long, String.
     * Тот же набор форматов, что в FirestoreUserDataSource.
     */
    private fun parseDate(value: Any?): Date? {
        return when (value) {
            null -> null
            is Date -> value
            is Timestamp -> value.toDate()
            is Long -> Date(value)
            is String -> {
                val formats = listOf(
                    "MMM d, yyyy HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd"
                )
                for (fmt in formats) {
                    try {
                        return java.text.SimpleDateFormat(fmt, java.util.Locale.ENGLISH).parse(value)
                    } catch (_: Exception) {
                        // пробуем следующий
                    }
                }
                null
            }
            else -> null
        }
    }
}

// ==================== ИСКЛЮЧЕНИЯ ====================

class InviteNotFoundException(code: String) :
    Exception("Инвайт-код не найден: $code")

class InviteExpiredException(code: String) :
    Exception("Срок действия кода истёк: $code")

class InviteAlreadyUsedException(code: String) :
    Exception("Код уже использован: $code")

class InviteAlreadyClaimedException(code: String) :
    Exception("Код уже используется другим устройством: $code")