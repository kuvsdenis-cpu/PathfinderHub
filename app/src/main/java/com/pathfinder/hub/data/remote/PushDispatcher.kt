package com.pathfinder.hub.data.remote

import android.util.Log
import com.pathfinder.hub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Клиент Yandex Cloud Function, которая рассылает FCM.
 *
 * Ожидаемый контракт функции:
 *   POST { userId, title, body, data }
 *   Функция сама берёт FCM-токены из users/{userId}/fcmTokens
 *   и рассылает push на все.
 *
 * Fire-and-forget: если упало — не критично, только лог.
 */
@Singleton
class PushDispatcher @Inject constructor() {

    companion object {
        private const val TAG = "PushDispatcher"
    }

    /**
     * Отправить push пользователю. Не бросает исключение.
     */
    suspend fun sendToUser(
        userId: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ) {
        if (userId.isBlank()) return
        val url = BuildConfig.YANDEX_PUSH_URL
        if (url.isBlank()) {
            Log.w(TAG, "YANDEX_PUSH_URL не задан — push пропущен")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("userId", userId)
                    put("title", title)
                    put("body", body)
                    val dataObj = JSONObject()
                    data.forEach { (k, v) -> dataObj.put(k, v) }
                    put("data", dataObj)
                }.toString()

                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5_000
                    readTimeout = 10_000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code in 200..299) {
                    Log.d(TAG, "push OK → $userId: $title")
                } else {
                    Log.w(TAG, "push HTTP $code → $userId")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "push failed → $userId (не критично)", e)
            }
        }
    }
}
