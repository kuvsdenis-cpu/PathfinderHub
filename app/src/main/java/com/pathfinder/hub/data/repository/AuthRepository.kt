package com.pathfinder.hub.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUserId: String? get() = auth.currentUser?.uid

    companion object {
        private const val TAG = "AuthRepository"
    }

    // ==================== РЕГИСТРАЦИЯ ====================

    suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<String> = try {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: return Result.failure(Exception("Пустой ответ Firebase"))

        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
        ).await()

        Result.success(user.uid)
    } catch (e: Exception) {
        Log.e(TAG, "registerWithEmail FAILED", e)
        Result.failure(e)
    }

    // ==================== ВХОД ====================

    suspend fun signInWithEmail(email: String, password: String): Result<String> = try {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val user = result.user ?: return Result.failure(Exception("Пустой ответ Firebase"))
        Result.success(user.uid)
    } catch (e: Exception) {
        Log.e(TAG, "signInWithEmail FAILED", e)
        Result.failure(e)
    }

    // ==================== ПОДТВЕРЖДЕНИЕ ПОЧТЫ ====================

    /**
     * Отправляет письмо с подтверждением email на текущий аккаунт.
     *
     * Firebase сам формирует письмо и отправляет его.
     * Пользователь переходит по ссылке — Firebase помечает emailVerified = true
     * на сервере. Приложение узнаёт об этом при следующем входе
     * (через reloadUser() + isEmailVerified).
     */
    suspend fun sendVerificationEmail(): Result<Unit> = try {
        val user = auth.currentUser
            ?: return Result.failure(Exception("Нет авторизованного пользователя"))

        user.sendEmailVerification().await()
        Log.d(TAG, "sendVerificationEmail OK: ${user.uid}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "sendVerificationEmail FAILED", e)
        Result.failure(e)
    }

    /**
     * Перезагружает данные пользователя с сервера Firebase.
     * Нужно, чтобы получить актуальное значение isEmailVerified —
     * оно меняется на сервере, когда пользователь переходит по ссылке.
     *
     * Без reload() локальный кэш Firebase может показывать старое значение.
     */
    suspend fun reloadUser(): Result<Boolean> = try {
        val user = auth.currentUser
            ?: return Result.failure(Exception("Нет авторизованного пользователя"))

        user.reload().await()
        val verified = auth.currentUser?.isEmailVerified ?: false
        Log.d(TAG, "reloadUser OK: uid=${user.uid}, emailVerified=$verified")
        Result.success(verified)
    } catch (e: Exception) {
        Log.e(TAG, "reloadUser FAILED", e)
        Result.failure(e)
    }

    // ==================== ВЫХОД ====================

    fun signOut() {
        auth.signOut()
    }

    // ==================== УДАЛЕНИЕ ТЕКУЩЕГО ПОЛЬЗОВАТЕЛЯ ====================

    /**
     * Удаляет текущий Firebase Auth аккаунт.
     *
     * Используется для отката при неудачной регистрации:
     * если Auth создан, но User не сохранился — нужно удалить Auth,
     * иначе останется «висячий» аккаунт, на который нельзя войти
     * (нет документа users/{uid}), но и email занят.
     *
     * Возвращает:
     *   - Result.success(Unit) — аккаунт удалён или его не было
     *   - Result.failure(e)    — не удалось удалить (например, требуется
     *                            недавняя аутентификация)
     *
     * ВАЖНО: Firebase может потребовать повторную аутентификацию
     * (recent login), если аккаунт создан давно. Для свежесозданного
     * аккаунта при регистрации этого не требуется.
     */
    suspend fun deleteCurrentUser(): Result<Unit> {
        val user = auth.currentUser
        if (user == null) {
            Log.d(TAG, "deleteCurrentUser: пользователя нет, нечего удалять")
            return Result.success(Unit)
        }

        return try {
            val uid = user.uid
            user.delete().await()
            Log.d(TAG, "deleteCurrentUser OK: $uid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteCurrentUser FAILED", e)
            Result.failure(e)
        }
    }
}