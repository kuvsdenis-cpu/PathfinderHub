#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub - Offline autologin fix (08.10.2026)
Запуск:
    python fix_offline_autologin.py
"""

import shutil
import sys
from pathlib import Path

ROOT = Path.cwd()


def die(msg):
    print("[ERROR] " + msg)
    sys.exit(1)


def patch(path, replacements, backup=True):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

    if backup:
        bak = p.with_suffix(p.suffix + ".bak")
        if not bak.exists():
            shutil.copy2(p, bak)
            print("[BAK]  " + bak.name)

    for i, (old, new) in enumerate(replacements, 1):
        if old not in text:
            die(path + ": шаблон #" + str(i) + " не найден.\n---\n"
                + old[:400] + "\n---")
        if text.count(old) > 1:
            print("[WARN] " + path + ": шаблон #" + str(i)
                  + " встречается " + str(text.count(old)) + " раз")
        text = text.replace(old, new, 1)

    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


patch("app/src/main/java/com/pathfinder/hub/MainActivity.kt", [
    # ---------- 1. Импорты ----------
    ("""import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier""",
     """import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier"""),

    # ---------- 2. onCreate: setContent СНАЧАЛА, FCM — в фоне ----------
    ("""        handleDeepLink(intent)
        askNotificationPermission()

        lifecycleScope.launch {
            val startDestination = resolveStartDestination()
            registerFcmTokenIfLoggedIn()
            checkAndNotifyUnreadIfLoggedIn()

            setContent {
                PathfinderTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        PathfinderNavHost(
                            navController = navController,
                            startDestination = startDestination
                        )
                    }
                }
            }
        }
    }""",
     """        handleDeepLink(intent)
        askNotificationPermission()

        // UI стартует СРАЗУ. resolveStartDestination() читает Room (~50 мс),
        // на это время показываем спиннер. FCM и summary-уведомление уходят
        // в отдельную корутину — без сети они могут висеть минуты, но UI
        // уже отрисован и работает.
        setContent {
            PathfinderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
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
                    }
                }
            }
        }

        // Фоновые задачи: FCM-токен, summary-уведомление.
        // Не блокируют UI. Без сети просто зависнут — это ожидаемо.
        lifecycleScope.launch {
            registerFcmTokenIfLoggedIn()
            checkAndNotifyUnreadIfLoggedIn()
        }
    }"""),
])

print()
print("Готово.")
print("Пересобери APK и проверь офлайн:")
print("  1. Включи авиарежим.")
print("  2. Полностью закрой приложение (свайп из Recents).")
print("  3. Открой снова.")
print("  4. Должен появиться 'Панель директора' с именем и клубом из Room.")