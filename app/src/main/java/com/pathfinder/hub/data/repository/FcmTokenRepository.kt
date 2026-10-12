package com.pathfinder.hub.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохраняет FCM-токены в Firestore.
 * Путь: users/{uid}/fcmTokens/{token}
 *
 * Защита от дублирования:
 *   - Mutex сериализует параллельные вызовы saveToken
 *     (onCreate + onResume + onNewToken).
 *   - In-memory guard отсекает повторы в рамках процесса.
 *   - SharedPreferences guard переживает перезапуск приложения.
 *
 * При removeCurrentToken guard сбрасывается, чтобы после
 * логаута/логина токен сохранился заново.
 */
@Singleton
class FcmTokenRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    @Volatile private var lastSavedUid: String? = null
    @Volatile private var lastSavedToken: String? = null

    suspend fun registerCurrentToken(userId: String): Result<String> {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isBlank()) {
                Result.failure(IllegalStateException("Пустой FCM-токен"))
            } else {
                saveToken(userId, token).fold(
                    onSuccess = { Result.success(token) },
                    onFailure = { Result.failure(it) },
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerCurrentToken failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveToken(userId: String, token: String): Result<Unit> = mutex.withLock {
        // 1. In-memory guard (быстро, без I/O)
        if (userId == lastSavedUid && token == lastSavedToken) {
            Log.d(TAG, "saveToken: skip (in-memory) uid=$userId token=${token.take(12)}…")
            return@withLock Result.success(Unit)
        }

        // 2. Persistent guard (SharedPreferences)
        val savedUid = prefs.getString(KEY_UID, null)
        val savedToken = prefs.getString(KEY_TOKEN, null)
        if (userId == savedUid && token == savedToken) {
            lastSavedUid = userId
            lastSavedToken = token
            Log.d(TAG, "saveToken: skip (persisted) uid=$userId token=${token.take(12)}…")
            return@withLock Result.success(Unit)
        }

        // 3. Реальная запись
        return@withLock try {
            val data = mapOf(
                "userId" to userId,
                "token" to token,
                "platform" to "android",
                "deviceId" to android.os.Build.MODEL,
                "updatedAt" to Timestamp(Date()),
            )
            firestore.collection("users")
                .document(userId)
                .collection("fcmTokens")
                .document(token)
                .set(data)
                .await()

            lastSavedUid = userId
            lastSavedToken = token
            prefs.edit()
                .putString(KEY_UID, userId)
                .putString(KEY_TOKEN, token)
                .apply()

            Log.d(TAG, "FCM token saved: ${token.take(12)}…")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "saveToken failed", e)
            Result.failure(e)
        }
    }

    /**
     * Удаляет текущий FCM-токен этого устройства из Firestore.
     * Вызывается при логауте.
     */
    suspend fun removeCurrentToken(userId: String): Result<Unit> = mutex.withLock {
        return@withLock try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isBlank()) {
                Result.success(Unit)
            } else {
                firestore.collection("users")
                    .document(userId)
                    .collection("fcmTokens")
                    .document(token)
                    .delete()
                    .await()

                // Сбрасываем guard'ы — иначе после повторного логина
                // saveToken решит, что токен уже сохранён, и ничего не сделает.
                if (lastSavedUid == userId && lastSavedToken == token) {
                    lastSavedUid = null
                    lastSavedToken = null
                    prefs.edit()
                        .remove(KEY_UID)
                        .remove(KEY_TOKEN)
                        .apply()
                }

                Log.d(TAG, "FCM token removed for $userId")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "removeCurrentToken failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "FcmTokenRepo"
        private const val PREFS_NAME = "fcm_token_guard"
        private const val KEY_UID = "last_uid"
        private const val KEY_TOKEN = "last_token"
    }
}
