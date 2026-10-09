#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FCM: регистрация токена через FirebaseAuth.addAuthStateListener.
Чтобы после логина внутри той же activity токен сохранялся.
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


def patch_main_activity() -> bool:
    print("\n🔧 Патчу MainActivity.kt...")
    f = PKG / "MainActivity.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "addAuthStateListener" in text:
        print("⚠️  Уже пропатчен")
        return True

    # 1. Импорт FirebaseAuth
    imp_anchor = "import com.pathfinder.hub.data.local.SessionManager"
    if imp_anchor not in text:
        print("❌ Нет якоря SessionManager import")
        return False
    text = text.replace(
        imp_anchor,
        "import com.google.firebase.auth.FirebaseAuth\n" + imp_anchor,
        1,
    )

    # 2. Поле authStateListener
    field_anchor = "    private val requestNotifPermission = registerForActivityResult("
    if field_anchor not in text:
        print("❌ Нет якоря requestNotifPermission")
        return False
    text = text.replace(
        field_anchor,
        "    private var authStateListener: FirebaseAuth.AuthStateListener? = null\n\n"
        + field_anchor,
        1,
    )

    # 3. Вызов setupAuthListener() в onCreate (после askNotificationPermission)
    hook = "        askNotificationPermission()"
    if hook not in text:
        print("❌ Нет якоря askNotificationPermission()")
        return False
    text = text.replace(
        hook,
        hook + "\n        setupAuthListener()",
        1,
    )

    # 4. Метод setupAuthListener перед registerFcmTokenIfLoggedIn
    method_anchor = "    private suspend fun registerFcmTokenIfLoggedIn() {"
    if method_anchor not in text:
        print("❌ Нет якоря registerFcmTokenIfLoggedIn")
        return False

    method = '''    /**
     * При смене пользователя (логин) — регистрируем FCM-токен для нового uid.
     * Работает независимо от того, пересоздалась activity или нет.
     */
    private fun setupAuthListener() {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                lifecycleScope.launch {
                    registerFcmTokenIfLoggedIn()
                }
            }
        }
        authStateListener = listener
        FirebaseAuth.getInstance().addAuthStateListener(listener)
    }

    override fun onDestroy() {
        authStateListener?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
        authStateListener = null
        super.onDestroy()
    }

    private suspend fun registerFcmTokenIfLoggedIn() {'''
    text = text.replace(method_anchor, method, 1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print("✅ MainActivity.kt пропатчен")
    return True


def main():
    print("=" * 60)
    print("  FCM: регистрация через AuthStateListener")
    print("=" * 60)
    ok = patch_main_activity()
    print("\n" + "=" * 60)
    if ok:
        print("✅ Сборка: .\\gradlew assembleDebug")
        print("\nТест:")
        print("  1. Войди под юзером А → users/{uidA}/fcmTokens — токен")
        print("  2. Выйди (без перезапуска) → users/{uidA}/fcmTokens — пусто")
        print("  3. Войди под юзером Б (без перезапуска)")
        print("  4. users/{uidB}/fcmTokens — токен")
        print("\nВ логах ожидаешь:")
        print("  FcmTokenRepo  D  FCM token saved: ...")
        print("  MainActivity  D  FCM registered: ...")
    else:
        print("❌ Не удалось")
    print("=" * 60)


if __name__ == "__main__":
    main()