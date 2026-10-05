package com.pathfinder.hub.domain.usecase.auth

import android.util.Log
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.audit.SecurityLogEntity
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.repository.AuthRepository
import com.pathfinder.hub.data.repository.UserRepository
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val userRepo: UserRepository,
    private val auditRepo: AuditRepository,
    private val authRepo: AuthRepository,
    private val sessionManager: SessionManager
) {

    companion object {
        private const val TAG = "LoginUseCase"
    }

    suspend operator fun invoke(email: String, password: String): Result<String> {
        val normalizedEmail = email.trim().lowercase()

        // 1. Локальная проверка: пользователь уже входил ранее?
        val sessionUserId = sessionManager.getUserId()
        if (!sessionUserId.isNullOrBlank()) {
            val localUser = userRepo.getUserLocalOnly(sessionUserId)
            if (localUser != null && localUser.email.equals(normalizedEmail, ignoreCase = true)) {
                auditLogin(localUser.id, success = true, details = "offline_session")
                return Result.success(localUser.id)
            }
        }

        // 2. Пытаемся войти через Firebase
        return try {
            val authResult = authRepo.signInWithEmail(normalizedEmail, password)
            val uid = authResult.getOrElse { authError ->
                val localUser = userRepo.getUserByEmail(normalizedEmail)
                if (localUser != null && !sessionUserId.isNullOrBlank()
                    && sessionUserId == localUser.id) {
                    auditLogin(localUser.id, success = true, details = "offline_fallback")
                    return Result.success(localUser.id)
                }
                return Result.failure(mapAuthError(authError))
            }

            val user = userRepo.getUser(uid)
                ?: return Result.failure(
                    Exception("Профиль не найден. Обратитесь к директору клуба.")
                )

            // Обновляем emailVerified из Firebase, если пользователь
            // подтвердил почту после регистрации.
            // reloadUser() тянет свежие данные с сервера Firebase —
            // без него локальный кэш может показывать старое значение.
            syncEmailVerified(uid)

            userRepo.update(user.copy(lastLoginAt = Date()))
            auditLogin(user.id, success = true, details = "firebase")

            Result.success(user.id)
        } catch (e: Exception) {
            Result.failure(mapAuthError(e))
        }
    }

    /**
     * Синхронизирует флаг emailVerified с сервером Firebase.
     *
     * Логика:
     *   1. Просим Firebase перезагрузить профиль (reload).
     *   2. Получаем актуальное значение isEmailVerified.
     *   3. Если оно изменилось — обновляем UserEntity в Room и Firestore.
     *
     * Не критично, если что-то упадёт: логируем и идём дальше.
     * Пользователь всё равно сможет работать, а флаг обновится
     * при следующем входе.
     */
    private suspend fun syncEmailVerified(uid: String) {
        try {
            val reloadResult = authRepo.reloadUser()
            val verified = reloadResult.getOrElse { e ->
                Log.w(TAG, "reloadUser упал: ${e.message}")
                return
            }

            Log.d(TAG, "emailVerified от Firebase: $verified для $uid")
            userRepo.updateEmailVerified(uid, verified)
        } catch (e: Exception) {
            Log.w(TAG, "syncEmailVerified упал", e)
        }
    }

    private suspend fun auditLogin(userId: String, success: Boolean, details: String?) {
        try {
            auditRepo.logSecurity(
                SecurityLogEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    action = "login",
                    ipHash = "",
                    userAgent = "android",
                    timestamp = Date(),
                    success = success,
                    details = details
                )
            )
        } catch (_: Exception) {
            // не падаем, если аудит не удался
        }
    }

    /**
     * Преобразуем технические ошибки Firebase в понятные сообщения.
     * Принимает Throwable, потому что Result.getOrElse даёт Throwable.
     */
    private fun mapAuthError(e: Throwable): Exception {
        val message = e.message ?: return Exception("Ошибка входа")

        return when {
            message.contains("network error", ignoreCase = true)
                    || message.contains("timeout", ignoreCase = true)
                    || message.contains("unable to resolve host", ignoreCase = true)
                    || e is java.net.UnknownHostException
                    || e is java.net.SocketTimeoutException
                    || e is java.net.ConnectException ->
                Exception("Нет подключения к интернету. Войдите онлайн хотя бы раз.")

            message.contains("password is invalid", ignoreCase = true)
                    || message.contains("wrong-password", ignoreCase = true) ->
                Exception("Неверный пароль")

            message.contains("no user record", ignoreCase = true)
                    || message.contains("user-not-found", ignoreCase = true) ->
                Exception("Пользователь с таким email не найден")

            message.contains("invalid email", ignoreCase = true) ->
                Exception("Некорректный email")

            message.contains("too many requests", ignoreCase = true)
                    || message.contains("too-many-requests", ignoreCase = true) ->
                Exception("Слишком много попыток. Попробуйте позже.")

            else -> Exception(message)
        }
    }
}