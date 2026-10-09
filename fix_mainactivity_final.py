#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MainActivity final fix: offline autologin + theme subscription.
Не трогает FCM, чаты, Yandex воркер.
Запуск:
    python fix_mainactivity_final.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")
PATH = "app/src/main/java/com/pathfinder/hub/MainActivity.kt"


def die(m): print("[ERROR] " + m); sys.exit(1)


p = ROOT / PATH
if not p.exists(): die("не найден: " + PATH)

raw = p.read_bytes()
crlf = b"\r\n" in raw
text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

if "settingsRepository.observeUserSettings" in text and "startDestination by remember" in text:
    print("[SKIP] MainActivity уже пропатчен.")
    sys.exit(0)

# Backup
bak = p.with_suffix(p.suffix + ".bak." + STAMP)
shutil.copy2(p, bak)
print("[BAK]  " + bak.name)

# 1. imports
imp_old = """import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier"""
imp_new = """import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier"""
if imp_old not in text:
    die("не нашёл import-блок в MainActivity")
text = text.replace(imp_old, imp_new, 1)

# 2. import SettingsRepository
imp2_old = """import com.pathfinder.hub.data.repository.FcmTokenRepository
import com.pathfinder.hub.data.repository.UserRepository"""
imp2_new = """import com.pathfinder.hub.data.repository.FcmTokenRepository
import com.pathfinder.hub.data.repository.SettingsRepository
import com.pathfinder.hub.data.repository.UserRepository"""
if imp2_old not in text:
    die("не нашёл import FcmTokenRepository/UserRepository")
text = text.replace(imp2_old, imp2_new, 1)

# 3. инжект SettingsRepository
inj_old = """    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var fcmTokenRepository: FcmTokenRepository"""
inj_new = """    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var fcmTokenRepository: FcmTokenRepository
    @Inject lateinit var settingsRepository: SettingsRepository"""
if inj_old not in text:
    die("не нашёл блок инжектов")
text = text.replace(inj_old, inj_new, 1)

# 4. onCreate
oncreate_old = """        handleDeepLink(intent)
        askNotificationPermission()

        lifecycleScope.launch {
            val startDestination = resolveStartDestination()
            registerFcmTokenIfLoggedIn()

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
    }"""

oncreate_new = """        handleDeepLink(intent)
        askNotificationPermission()

        // UI стартует СРАЗУ (офлайн-автологин). resolveStartDestination() читает Room.
        setContent {
            val uid = sessionManager.getUserId()
            var themePref by remember { mutableStateOf("system") }
            LaunchedEffect(uid) {
                if (uid != null) {
                    settingsRepository.observeUserSettings(uid).collect { s ->
                        themePref = s?.theme ?: "system"
                    }
                }
            }
            val dark = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            PathfinderTheme(darkTheme = dark) {
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

        // Фоновые задачи: FCM, summary. Не блокируют UI.
        lifecycleScope.launch {
            registerFcmTokenIfLoggedIn()
        }
    }"""

if oncreate_old not in text:
    die("не нашёл onCreate-блок. Возможно, файл уже другой.")
text = text.replace(oncreate_old, oncreate_new, 1)

# Запись
p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
print("[OK]   " + PATH)
print()
print("Готово.")
print("Собери: .\\gradlew assembleDebug")
print("Поставь: .\\gradlew installDebug")