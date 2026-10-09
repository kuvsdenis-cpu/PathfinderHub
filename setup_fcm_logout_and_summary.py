#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub — A + C:
  A. Удаление FCM-токена при логауте.
  C. Summary-уведомление при логине, если есть новые сообщения.
"""

import shutil
import sys
from pathlib import Path


def find_project_root() -> Path:
    here = Path.cwd()
    for p in [here] + list(here.parents):
        if (p / "settings.gradle.kts").exists() or (p / "settings.gradle").exists():
            return p
    print("❌ Не найден корень проекта.")
    sys.exit(1)


ROOT = find_project_root()
PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "pathfinder" / "hub"


def backup(f: Path):
    shutil.copy2(f, f.with_suffix(f.suffix + ".bak"))


# ============================================================
# A.1 — FcmTokenRepository: removeCurrentToken
# ============================================================
def patch_token_repo() -> bool:
    print("\n🔧 A.1 FcmTokenRepository.kt...")
    f = PKG / "data/repository/FcmTokenRepository.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "removeCurrentToken" in text:
        print("⚠️  Уже пропатчен")
        return True

    anchor = "    companion object {"
    if anchor not in text:
        print("❌ Не найден companion object")
        return False

    method = '''    /**
     * Удаляет текущий FCM-токен этого устройства из Firestore.
     * Вызывается при логауте.
     */
    suspend fun removeCurrentToken(userId: String): Result<Unit> {
        return try {
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
                Log.d(TAG, "FCM token removed for $userId")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "removeCurrentToken failed", e)
            Result.failure(e)
        }
    }

    companion object {'''
    text = text.replace(anchor, method, 1)
    backup(f)
    f.write_text(text, encoding="utf-8")
    print("✅ FcmTokenRepository.kt пропатчен")
    return True


# ============================================================
# A.2 — TeenProfileViewModel.logout: удаляем токен
# ============================================================
def patch_teen_vm() -> bool:
    print("\n🔧 A.2 TeenProfileViewModel.kt...")
    f = PKG / "ui/screens/profile/TeenProfileViewModel.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "fcmTokenRepository" in text:
        print("⚠️  Уже пропатчен")
        return True

    # Импорт
    imp = "import com.pathfinder.hub.data.repository.LevelRepository"
    if imp not in text:
        print("❌ Нет якоря импорта LevelRepository")
        return False
    text = text.replace(
        imp,
        imp + "\nimport com.pathfinder.hub.data.repository.FcmTokenRepository",
        1,
    )

    # Конструктор
    ctor_old = "    private val authRepository: AuthRepository    // <-- ДОБАВЛЕНО\n) : ViewModel() {"
    ctor_new = ("    private val authRepository: AuthRepository,\n"
                "    private val fcmTokenRepository: FcmTokenRepository\n"
                ") : ViewModel() {")
    if ctor_old not in text:
        print("❌ Нет якоря конструктора")
        return False
    text = text.replace(ctor_old, ctor_new, 1)

    # logout
    old = '''    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (_: Exception) {
                // Игнорируем ошибку Firebase — всё равно чистим локальную сессию.
            }
            sessionManager.clear()
            _state.update { it.copy(loggedOut = true) }
        }
    }'''
    new = '''    fun logout() {
        viewModelScope.launch {
            val uid = sessionManager.getUserId()
            if (uid != null) {
                try { fcmTokenRepository.removeCurrentToken(uid) } catch (_: Exception) {}
            }
            try {
                authRepository.signOut()
            } catch (_: Exception) {
                // Игнорируем ошибку Firebase — всё равно чистим локальную сессию.
            }
            sessionManager.clear()
            _state.update { it.copy(loggedOut = true) }
        }
    }'''
    if old not in text:
        print("❌ Не найден метод logout()")
        return False
    text = text.replace(old, new, 1)

    backup(f)
    f.write_text(text, encoding="utf-8")
    print("✅ TeenProfileViewModel.kt пропатчен")
    return True


# ============================================================
# A.3 — DirectorHomeViewModel.logout: удаляем токен
# ============================================================
def patch_director_vm() -> bool:
    print("\n🔧 A.3 DirectorHomeViewModel.kt...")
    f = PKG / "ui/screens/home/director/DirectorHomeViewModel.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "fcmTokenRepository" in text:
        print("⚠️  Уже пропатчен")
        return True

    imp = "import com.pathfinder.hub.data.repository.ClubRepository"
    if imp not in text:
        print("❌ Нет якоря импорта ClubRepository")
        return False
    text = text.replace(
        imp,
        imp + "\nimport com.pathfinder.hub.data.repository.FcmTokenRepository",
        1,
    )

    ctor_old = "    private val authRepository: AuthRepository    // <-- ДОБАВЛЕНО\n) : ViewModel() {"
    ctor_new = ("    private val authRepository: AuthRepository,\n"
                "    private val fcmTokenRepository: FcmTokenRepository\n"
                ") : ViewModel() {")
    if ctor_old not in text:
        print("❌ Нет якоря конструктора")
        return False
    text = text.replace(ctor_old, ctor_new, 1)

    old = '''    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (_: Exception) {
                // Игнорируем ошибку Firebase — всё равно чистим локальную сессию.
            }
            sessionManager.clear()
            _state.update { it.copy(loggedOut = true) }
        }
    }'''
    new = '''    fun logout() {
        viewModelScope.launch {
            val uid = sessionManager.getUserId()
            if (uid != null) {
                try { fcmTokenRepository.removeCurrentToken(uid) } catch (_: Exception) {}
            }
            try {
                authRepository.signOut()
            } catch (_: Exception) {
                // Игнорируем ошибку Firebase — всё равно чистим локальную сессию.
            }
            sessionManager.clear()
            _state.update { it.copy(loggedOut = true) }
        }
    }'''
    if old not in text:
        print("❌ Не найден метод logout()")
        return False
    text = text.replace(old, new, 1)

    backup(f)
    f.write_text(text, encoding="utf-8")
    print("✅ DirectorHomeViewModel.kt пропатчен")
    return True


# ============================================================
# C.1 — SessionManager: lastLoginAt
# ============================================================
def patch_session_manager() -> bool:
    print("\n🔧 C.1 SessionManager.kt...")
    f = PKG / "data/local/SessionManager.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "getLastLoginAt" in text:
        print("⚠️  Уже пропатчен")
        return True

    anchor = '''    fun clear() {
        prefs.edit().clear().apply()
    }'''
    if anchor not in text:
        print("❌ Не найден clear()")
        return False

    new_methods = '''    fun getLastLoginAt(): Long? {
        val v = prefs.getLong(KEY_LAST_LOGIN_AT, 0L)
        return if (v > 0L) v else null
    }

    fun setLastLoginAt(ms: Long) {
        prefs.edit().putLong(KEY_LAST_LOGIN_AT, ms).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }'''
    text = text.replace(anchor, new_methods, 1)

    # ключ
    old_key = 'private const val KEY_USER_ID = "user_id"'
    new_key = ('private const val KEY_USER_ID = "user_id"\n'
               '        private const val KEY_LAST_LOGIN_AT = "last_login_at"')
    if old_key not in text:
        print("❌ Нет якоря KEY_USER_ID")
        return False
    text = text.replace(old_key, new_key, 1)

    backup(f)
    f.write_text(text, encoding="utf-8")
    print("✅ SessionManager.kt пропатчен")
    return True


# ============================================================
# C.2 — ChatRepository: countChatsWithNewMessages
# ============================================================
def patch_chat_repo() -> bool:
    print("\n🔧 C.2 ChatRepository.kt...")
    f = PKG / "data/repository/ChatRepository.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "countChatsWithNewMessages" in text:
        print("⚠️  Уже пропатчен")
        return True

    anchor = "    // ==================== Утилиты ===================="
    if anchor not in text:
        print("❌ Нет якоря Утилиты")
        return False

    method = '''    /**
     * Считает сколько DM/групп у пользователя имеют
     * последнее сообщение позже [sinceMs].
     * Используется для summary-уведомления при логине.
     */
    suspend fun countChatsWithNewMessages(
        clubId: String, userId: String, sinceMs: Long,
    ): Int {
        val dms = try { getMyDmsOnce(clubId, userId) } catch (_: Exception) { emptyList() }
        val groups = try { getMyGroupsOnce(clubId, userId) } catch (_: Exception) { emptyList() }
        val dmCount = dms.count { (it.lastMessageAt?.time ?: 0L) > sinceMs }
        val grCount = groups.count { (it.lastMessageAt?.time ?: 0L) > sinceMs }
        return dmCount + grCount
    }

    // ==================== Утилиты ===================='''
    text = text.replace(anchor, method, 1)

    backup(f)
    f.write_text(text, encoding="utf-8")
    print("✅ ChatRepository.kt пропатчен")
    return True


# ============================================================
# C.3 — ChatNotificationHelper: notifySummary
# ============================================================
def patch_notifier() -> bool:
    print("\n🔧 C.3 ChatNotificationHelper.kt...")
    f = PKG / "notifications/ChatNotificationHelper.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "notifySummary" in text:
        print("⚠️  Уже пропатчен")
        return True

    anchor = "    private fun buildIntent(type: String, targetId: String, otherUid: String?): PendingIntent {"
    if anchor not in text:
        print("❌ Не найден buildIntent")
        return False

    method = '''    /**
     * Одно общее уведомление "N чатов с новыми сообщениями".
     * Показывается при холодном старте, если с прошлого логина
     * появились новые сообщения в DM/группах.
     */
    fun notifySummary(count: Int) {
        ensureChannel()
        val title = "Новые сообщения"
        val body = when (count) {
            1 -> "У вас 1 чат с новыми сообщениями"
            else -> "У вас $count чатов с новыми сообщениями"
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TYPE, "chats_list")
            putExtra(EXTRA_TARGET_ID, "")
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(context, 999_999, intent, flags)
        show(999_999, title, body, pi)
    }

    private fun buildIntent(type: String, targetId: String, otherUid: String?): PendingIntent {'''
    text = text.replace(anchor, method, 1)

    backup(f)
    f.write_text(text, encoding="utf-8")
    print("✅ ChatNotificationHelper.kt пропатчен")
    return True


# ============================================================
# C.4 — MainActivity: checkAndNotifyUnreadIfLoggedIn
# ============================================================
def patch_main_activity() -> bool:
    print("\n🔧 C.4 MainActivity.kt...")
    f = PKG / "MainActivity.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "checkAndNotifyUnreadIfLoggedIn" in text:
        print("⚠️  Уже пропатчен")
        return True

    # Импорты
    imp_anchor = "import com.pathfinder.hub.data.repository.FcmTokenRepository"
    if imp_anchor not in text:
        print("❌ Нет якоря импорта FcmTokenRepository")
        return False
    text = text.replace(
        imp_anchor,
        imp_anchor
        + "\nimport com.pathfinder.hub.data.repository.ChatRepository"
        + "\nimport com.pathfinder.hub.notifications.ChatNotificationHelper",
        1,
    )

    # Инжект ChatRepository
    inject_anchor = "    @Inject lateinit var fcmTokenRepository: FcmTokenRepository"
    if inject_anchor not in text:
        print("❌ Нет якоря fcmTokenRepository")
        return False
    text = text.replace(
        inject_anchor,
        inject_anchor
        + "\n    @Inject lateinit var chatRepository: ChatRepository"
        + "\n    @Inject lateinit var chatNotifier: ChatNotificationHelper",
        1,
    )

    # Вызов в onCreate после registerFcmTokenIfLoggedIn
    hook = "            registerFcmTokenIfLoggedIn()"
    if hook not in text:
        print("❌ Нет якоря registerFcmTokenIfLoggedIn()")
        return False
    text = text.replace(
        hook,
        hook + "\n            checkAndNotifyUnreadIfLoggedIn()",
        1,
    )

    # Метод — вставим перед registerFcmTokenIfLoggedIn
    method_anchor = "    private suspend fun registerFcmTokenIfLoggedIn() {"
    if method_anchor not in text:
        print("❌ Нет якоря registerFcmTokenIfLoggedIn")
        return False

    method = '''    /**
     * При холодном старте: если с прошлого логина появились новые
     * сообщения в DM/группах — показать summary-уведомление.
     */
    private suspend fun checkAndNotifyUnreadIfLoggedIn() {
        val uid = sessionManager.getUserId() ?: return
        val user = userRepository.getUserLocalOnly(uid) ?: return
        val clubId = user.clubId
        if (clubId.isNullOrBlank()) return

        val lastLogin = sessionManager.getLastLoginAt()
        val now = System.currentTimeMillis()

        if (lastLogin != null) {
            try {
                val count = chatRepository.countChatsWithNewMessages(clubId, uid, lastLogin)
                if (count > 0) {
                    chatNotifier.notifySummary(count)
                    Log.d(TAG, "Summary notification: $count chat(s)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkAndNotifyUnread failed: ${e.message}")
            }
        }

        sessionManager.setLastLoginAt(now)
    }

    private suspend fun registerFcmTokenIfLoggedIn() {'''
    text = text.replace(method_anchor, method, 1)

    backup(f)
    f.write_text(text, encoding="utf-8")
    print("✅ MainActivity.kt пропатчен")
    return True


# ============================================================
# C.5 — PathfinderNavHost: обработка "chats_list"
# ============================================================
def patch_navhost() -> bool:
    print("\n🔧 C.5 PathfinderNavHost.kt...")
    f = PKG / "ui/navigation/PathfinderNavHost.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if '"chats_list" ->' in text:
        print("⚠️  Уже пропатчен")
        return True

    old = '''                "club_chat" -> navController.navigate(Routes.CLUB_CHAT)'''
    new = '''                "club_chat" -> navController.navigate(Routes.CLUB_CHAT)
                "chats_list" -> navController.navigate(Routes.CHATS_LIST)'''
    if old not in text:
        print("❌ Не найден case 'club_chat' в LaunchedEffect deep links")
        print("   Проверь, что deep links были пропатчены раньше.")
        return False

    text = text.replace(old, new, 1)
    backup(f)
    f.write_text(text, encoding="utf-8")
    print("✅ PathfinderNavHost.kt пропатчен")
    return True


# ============================================================
# MAIN
# ============================================================
def main():
    print("=" * 60)
    print("  Pathfinder Hub — A (logout token) + C (summary)")
    print("=" * 60)

    results = {
        "FcmTokenRepository.kt":     patch_token_repo(),
        "TeenProfileViewModel.kt":   patch_teen_vm(),
        "DirectorHomeViewModel.kt":  patch_director_vm(),
        "SessionManager.kt":         patch_session_manager(),
        "ChatRepository.kt":         patch_chat_repo(),
        "ChatNotificationHelper.kt": patch_notifier(),
        "MainActivity.kt":           patch_main_activity(),
        "PathfinderNavHost.kt":      patch_navhost(),
    }

    print("\n" + "=" * 60)
    for k, v in results.items():
        print(f"  {'✅' if v else '❌'} {k}")

    if all(results.values()):
        print("\n✅ Готово.")
        print("\nСборка: .\\gradlew assembleDebug")
        print("\nТест A (logout удаляет токен):")
        print("  1. Войди под юзером А → users/{uidA}/fcmTokens — токен есть")
        print("  2. Нажми «Выйти» → users/{uidA}/fcmTokens — пусто")
        print("\nТест C (summary при логине):")
        print("  1. Войди под юзером А, нажми «Выйти»")
        print("  2. Через второе устройство напиши DM юзеру А")
        print("  3. Войди снова под А")
        print("  4. При холодном старте приложения → push «У вас N чатов…»")
        print("  5. Тап → открывается ChatsListScreen")
    else:
        print("\n⚠️  Смотри .bak рядом с файлами.")

    print("=" * 60)


if __name__ == "__main__":
    main()