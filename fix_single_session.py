#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Single-session lock (J) - 09.10.2026

- Firestore subcollection: users/{uid}/session/main
- Локально: sessionId + sessionAt (SharedPreferences)
- Опрос Firestore каждые 30 сек
- При вытеснении — callback → диалог → Login

Запуск:
    python fix_single_session.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        print("[SKIP] " + path + " (уже есть)")
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


def patch(path, repls, skip_if_marker=None):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
    if skip_if_marker and skip_if_marker in text:
        print("[SKIP] " + path + " (уже пропатчен)")
        return
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    for i, (o, n) in enumerate(repls, 1):
        if o not in text:
            die(path + ": шаблон #" + str(i) + ":\n" + o[:400])
        text = text.replace(o, n, 1)
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. FirestoreSessionDataSource.kt
# ============================================================
DS = '''package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Одна активная сессия на пользователя.
 *
 * Путь: users/{uid}/session/main
 * Поля: activeSessionId, activeSessionAt, deviceModel
 */
@Singleton
class FirestoreSessionDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreSessionDS"
        const val COLLECTION = "session"
        const val DOC_ID = "main"
    }

    private fun docRef(userId: String) =
        firestore.collection("users").document(userId).collection(COLLECTION).document(DOC_ID)

    data class RemoteSession(
        val sessionId: String,
        val sessionAt: Long,
        val deviceModel: String?,
    )

    suspend fun read(userId: String): RemoteSession? = try {
        val snap = docRef(userId).get().await()
        if (!snap.exists()) null
        else {
            val sid = snap.getString("activeSessionId")
            val at = snap.getTimestamp("activeSessionAt")?.toDate()?.time
            val model = snap.getString("deviceModel")
            if (sid != null && at != null) RemoteSession(sid, at, model) else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "read failed for $userId", e)
        null
    }

    suspend fun write(userId: String, sessionId: String, sessionAtMs: Long, deviceModel: String?): Boolean = try {
        docRef(userId).set(
            mapOf(
                "activeSessionId" to sessionId,
                "activeSessionAt" to Timestamp(java.util.Date(sessionAtMs)),
                "deviceModel" to deviceModel,
            )
        ).await()
        Log.d(TAG, "session written: $userId -> ${sessionId.take(8)}…")
        true
    } catch (e: Exception) {
        Log.w(TAG, "write failed for $userId", e)
        false
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/remote/FirestoreSessionDataSource.kt", DS)


# ============================================================
# 2. SessionManager.kt — sessionId + sessionAt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/SessionManager.kt", [
    ("""    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LAST_LOGIN_AT = "last_login_at"
    }""",
     """    // ---------- Single-session ----------

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
    }"""),
], skip_if_marker="KEY_SESSION_ID")


# ============================================================
# 3. SessionGuard.kt
# ============================================================
GUARD = '''package com.pathfinder.hub.data.session

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
'''
write_new("app/src/main/java/com/pathfinder/hub/data/session/SessionGuard.kt", GUARD)


# ============================================================
# 4. MainActivity.kt — hook + eviction dialog
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/MainActivity.kt", [
    # импорты
    ("import com.pathfinder.hub.data.local.SessionManager",
     "import androidx.compose.material3.AlertDialog\n"
     "import androidx.compose.material3.TextButton\n"
     "import androidx.compose.material3.Text\n"
     "import androidx.compose.runtime.DisposableEffect\n"
     "import com.pathfinder.hub.data.local.SessionManager\n"
     "import com.pathfinder.hub.data.session.SessionGuard"),

    # инжект
    ("""    @Inject lateinit var settingsRepository: SettingsRepository""",
     """    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sessionGuard: SessionGuard"""),

    # eviction state + guard start + dialog
    ("""    // UI стартует СРАЗУ (офлайн-автологин). resolveStartDestination() читает Room.
        setContent {
            val uid = sessionManager.getUserId()""",
     """    // UI стартует СРАЗУ (офлайн-автологин). resolveStartDestination() читает Room.
        setContent {
            val uid = sessionManager.getUserId()
            var evicted by remember { mutableStateOf(false) }

            // Single-session guard — только когда залогинен
            DisposableEffect(uid) {
                if (uid != null) {
                    sessionGuard.start(uid) { evicted = true }
                } else {
                    sessionGuard.stop()
                }
                onDispose { sessionGuard.stop() }
            }"""),

    # диалог внутри NavHost-контейнера
    ("""                    // Пока Room ещё читается, показываем ROUTE_HOME — RoleRouter
                    // сам решит, куда идти (director/teen/etc). Так NavHost всегда
                    // поднят, и logout-навигация сразу работает.
                    val dest = startDestination ?: Routes.ROLE_HOME
                    PathfinderNavHost(
                        navController = navController,
                        startDestination = dest
                    )""",
     """                    // Пока Room ещё читается, показываем ROUTE_HOME — RoleRouter
                    // сам решит, куда идти (director/teen/etc). Так NavHost всегда
                    // поднят, и logout-навигация сразу работает.
                    val dest = startDestination ?: Routes.ROLE_HOME
                    PathfinderNavHost(
                        navController = navController,
                        startDestination = dest
                    )

                    // Диалог вытеснения
                    if (evicted) {
                        AlertDialog(
                            onDismissRequest = { /* нельзя закрыть */ },
                            title = { Text("Сессия завершена") },
                            text = { Text(
                                "Вы вошли с другого устройства. Чтобы продолжить здесь, войдите заново."
                            ) },
                            confirmButton = {
                                TextButton(onClick = {
                                    sessionGuard.stop()
                                    sessionManager.clear()
                                    evicted = false
                                    navController.navigate(Routes.LOGIN) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }) { Text("Войти заново") }
                            }
                        )
                    }"""),
], skip_if_marker="sessionGuard.start(uid)")


# ============================================================
# 5. firestore.rules
# ============================================================
patch("firestore.rules", [
    ("""      match /fcmTokens/{tokenId} {
        allow read, write: if isSignedIn() && uid() == userId;
      }""",
     """      match /fcmTokens/{tokenId} {
        allow read, write: if isSignedIn() && uid() == userId;
      }
      match /session/{docId} {
        allow read, write: if isSignedIn() && uid() == userId;
      }"""),
], skip_if_marker="match /session/{docId}")


print()
print("=" * 60)
print("Готово.")
print("=" * 60)
print()
print("Дальше:")
print("  1. Сборка:")
print("       .\\gradlew assembleDebug")
print("       .\\gradlew installDebug")
print("  2. Задеплой правила:")
print("       firebase deploy --only firestore:rules")
print()
print("Проверка:")
print("  1. Устройство A: войти под teen → открыть любой экран")
print("  2. Устройство B: войти под ТЕМ ЖЕ teen")
print("  3. На устройстве A через 30-35 сек появится диалог")
print("     'Сессия завершена' → нажать → LOGIN")
print("  4. На устройстве B всё работает")
print()
print("Firestore: users/{uid}/session/main = { activeSessionId, activeSessionAt, deviceModel }")
print()
print("Откат: .bak.<timestamp>")