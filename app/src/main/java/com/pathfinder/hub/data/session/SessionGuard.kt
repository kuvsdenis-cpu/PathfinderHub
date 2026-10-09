package com.pathfinder.hub.data.session

import android.os.Build
import android.util.Log
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.remote.FirestoreSessionDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-session lock.
 *
 * Логика:
 *   1. При старте — генерим локальный sessionId (если нет) и пишем
 *      его в Firestore (best-effort).
 *   2. Каждые 30 сек опрашиваем Firestore.
 *   3. Если remote.activeSessionAt > my.sessionAt И remote.id != my.id
 *      → вытеснены → onEvicted().
 *   4. Если remote старее моего → перезаписываем (я новее).
 *
 * Офлайн: опрос падает, ничего не делаем — продолжаем работать.
 */
@Singleton
class SessionGuard @Inject constructor(
    private val sessionManager: SessionManager,
    private val firestore: FirestoreSessionDataSource,
) {

    private var job: Job? = null

    /**
     * Старт. Идемпотентно: повторный вызов сначала останавливает прошлый цикл.
     */
    fun start(userId: String, onEvicted: () -> Unit) {
        stop()
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // Гарантируем локальный sessionId
            var myId = sessionManager.getSessionId()
            var myAt = sessionManager.getSessionAt()
            if (myId == null || myAt == 0L) {
                myId = UUID.randomUUID().toString()
                myAt = System.currentTimeMillis()
                sessionManager.saveSession(myId, myAt)
            }

            // Первичная запись в Firestore (best-effort)
            tryWriteRemote(userId, myId, myAt)

            delay(INITIAL_DELAY_MS)

            while (isActive) {
                // локальный id мог поменяться при logout→login
                val currentId = sessionManager.getSessionId() ?: break
                val currentAt = sessionManager.getSessionAt()
                if (currentId != myId) { myId = currentId; myAt = currentAt }

                val remote = firestore.read(userId)
                if (remote == null) {
                    // Firestore недоступен / пусто → best-effort write
                    tryWriteRemote(userId, myId, myAt)
                } else {
                    when {
                        remote.sessionId == myId -> {
                            // Мы активны, всё ок
                        }
                        remote.sessionAt > myAt -> {
                            // Кто-то вошёл позже — вытеснены
                            Log.w(TAG, "evicted: remote=${remote.sessionId.take(8)}… > mine=${myId.take(8)}…")
                            withContext(Dispatchers.Main) { onEvicted() }
                            break
                        }
                        else -> {
                            // Мы новее — перезаписываем
                            tryWriteRemote(userId, myId, myAt)
                        }
                    }
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun tryWriteRemote(userId: String, sessionId: String, sessionAt: Long) {
        val model = Build.MANUFACTURER + " " + Build.MODEL
        firestore.write(userId, sessionId, sessionAt, model)
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val TAG = "SessionGuard"
        private const val POLL_INTERVAL_MS = 30_000L
        private const val INITIAL_DELAY_MS = 3_000L
    }
}
