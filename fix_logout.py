#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Fix logout: сессия чистится первой, FCM — в фоне.
И починка MainActivity: NavHost создаётся всегда, startDestination-фолбэк.
Запуск:
    python fix_logout.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m):
    print("[ERROR] " + m); sys.exit(1)


def patch(path, repls):
    p = ROOT / path
    if not p.exists(): die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
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
# 1. DirectorHomeViewModel.logout — мгновенная очистка, FCM в фоне
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeViewModel.kt", [
    ("""    fun logout() {
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
    }""",
     """    fun logout() {
        // Сначала — синхронно, без сети:
        val uid = sessionManager.getUserId()
        sessionManager.clear()
        _state.update { it.copy(loggedOut = true) }

        // Потом — в фоне, не блокирует UI:
        viewModelScope.launch {
            try { authRepository.signOut() } catch (_: Exception) {}
            if (uid != null) {
                try { fcmTokenRepository.removeCurrentToken(uid) } catch (_: Exception) {}
            }
        }
    }"""),
])


# ============================================================
# 2. TeenProfileViewModel.logout — то же самое
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/profile/TeenProfileViewModel.kt", [
    ("""    fun logout() {
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
    }""",
     """    fun logout() {
        // Сначала — синхронно, без сети:
        val uid = sessionManager.getUserId()
        sessionManager.clear()
        _state.update { it.copy(loggedOut = true) }

        // Потом — в фоне, не блокирует UI:
        viewModelScope.launch {
            try { authRepository.signOut() } catch (_: Exception) {}
            if (uid != null) {
                try { fcmTokenRepository.removeCurrentToken(uid) } catch (_: Exception) {}
            }
        }
    }"""),
])


# ============================================================
# 3. MainActivity — startDestination-фолбэк "role_home" вместо спиннера
#    (чтобы NavHost был всегда готов принять navigate(LOGIN))
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/MainActivity.kt", [
    ("""                    val navController = rememberNavController()
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        startDestination = resolveStartDestination()
                    }

                    val dest = startDestination
                    if (dest == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        PathfinderNavHost(
                            navController = navController,
                            startDestination = dest
                        )
                    }""",
     """                    val navController = rememberNavController()
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        startDestination = resolveStartDestination()
                    }

                    // Пока Room ещё читается, показываем ROUTE_HOME — RoleRouter
                    // сам решит, куда идти (director/teen/etc). Так NavHost всегда
                    // поднят, и logout-навигация сразу работает.
                    val dest = startDestination ?: Routes.ROLE_HOME
                    PathfinderNavHost(
                        navController = navController,
                        startDestination = dest
                    )"""),
])

print()
print("Готово. Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Проверка:")
print("  1. Открой под директором → Настройки видно?")
print("  2. Нажми Выйти → диалог → Выйти → должен вернуться на LOGIN за 0.5 сек.")
print("  3. То же под teen — профиль → Выйти.")
print()
print("Откат (если сломается ещё что-то):")
print("  Все .bak.<timestamp> лежат рядом с файлами.")