package com.pathfinder.hub.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pathfinder_session", Context.MODE_PRIVATE)

    fun saveUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getLastLoginAt(): Long? {
        val v = prefs.getLong(KEY_LAST_LOGIN_AT, 0L)
        return if (v > 0L) v else null
    }

    fun setLastLoginAt(ms: Long) {
        prefs.edit().putLong(KEY_LAST_LOGIN_AT, ms).apply()
    }

    // ---------- Single-session ----------

    fun getSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)

    fun getSessionAt(): Long {
        return prefs.getLong(KEY_SESSION_AT, 0L)
    }

    fun saveSession(sessionId: String, sessionAt: Long) {
        prefs.edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putLong(KEY_SESSION_AT, sessionAt)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_SESSION_AT)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LAST_LOGIN_AT = "last_login_at"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_SESSION_AT = "session_at"
    }
}