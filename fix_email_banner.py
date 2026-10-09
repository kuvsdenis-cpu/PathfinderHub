#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub - Email Verification Banner (08.10.2026)
Создаёт баннер "Почта не подтверждена" и патчит TeenHome.

Backup: перед каждой правкой создаётся .bak рядом с файлом.
Запуск:
    python fix_email_banner.py
"""

import shutil
import sys
from pathlib import Path

ROOT = Path.cwd()


def die(msg):
    print("[ERROR] " + msg)
    sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


def patch(path, replacements, backup=True):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

    # backup
    if backup:
        bak = p.with_suffix(p.suffix + ".bak")
        if not bak.exists():
            shutil.copy2(p, bak)
            print("[BAK]  " + str(bak.name))

    for i, (old, new) in enumerate(replacements, 1):
        if old not in text:
            die(path + ": шаблон #" + str(i) + " не найден.\n---\n" + old[:400] + "\n---")
        if text.count(old) > 1:
            print("[WARN] " + path + ": шаблон #" + str(i) + " встречается "
                  + str(text.count(old)) + " раз, заменяю первый")
        text = text.replace(old, new, 1)

    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. EmailVerificationBanner.kt
# ============================================================
BANNER = '''package com.pathfinder.hub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Баннер "Почта не подтверждена".
 *
 * Показывается в TeenHome (и опционально в других местах),
 * если UserEntity.emailVerified == false.
 *
 * @param isSending   true, пока идёт отправка письма
 * @param message     сообщение об успехе/ошибке отправки (null — показываем дефолтный текст)
 * @param onResend    колбэк на "Отправить повторно"
 */
@Composable
fun EmailVerificationBanner(
    isSending: Boolean,
    message: String?,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Почта не подтверждена",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = message
                    ?: "Подтвердите почту — мы отправили письмо со ссылкой. Без подтверждения часть функций может быть недоступна.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onResend,
                    enabled = !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Отправить повторно")
                }
            }
        }
    }
}
'''

write_new("app/src/main/java/com/pathfinder/hub/ui/components/EmailVerificationBanner.kt", BANNER)


# ============================================================
# 2. TeenHomeScreen.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/teen/TeenHomeScreen.kt", [
    # import
    ("import com.pathfinder.hub.ui.theme.PathfinderBlue",
     "import com.pathfinder.hub.ui.components.EmailVerificationBanner\n"
     "import com.pathfinder.hub.ui.theme.PathfinderBlue"),

    # Вставка баннера после приветствия
    ("""            Text(
                text = "Привет, $firstName!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            // Карточка текущей ступени""",
     """            Text(
                text = "Привет, $firstName!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // Плашка "Почта не подтверждена"
            if (state.user?.emailVerified == false) {
                EmailVerificationBanner(
                    isSending = state.resendingVerification,
                    message = state.verificationSentMessage,
                    onResend = viewModel::resendVerificationEmail
                )
            }

            // Карточка текущей ступени"""),
])


# ============================================================
# 3. TeenHomeViewModel.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/teen/TeenHomeViewModel.kt", [
    # imports
    ("import androidx.lifecycle.ViewModel",
     "import androidx.lifecycle.ViewModel\nimport com.google.firebase.auth.FirebaseAuth"),

    ("import kotlinx.coroutines.launch",
     "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.tasks.await"),

    # state: добавить поля
    ("""    val recentAchievements: List<AchievementProgressEntity> = emptyList(),
    val errorMessage: String? = null
)""",
     """    val recentAchievements: List<AchievementProgressEntity> = emptyList(),
    val errorMessage: String? = null,
    val resendingVerification: Boolean = false,
    val verificationSentMessage: String? = null
)"""),

    # метод resendVerificationEmail в конец класса
    ("""        // Последние ачивки
        viewModelScope.launch {
            achievementRepository.observeEarned(userId).collect { achievements ->
                _state.update {
                    it.copy(
                        recentAchievements = achievements
                            .sortedByDescending { a -> a.earnedAt?.time ?: 0 }
                            .take(3),
                        isLoading = false
                    )
                }
            }
        }
    }
}""",
     """        // Последние ачивки
        viewModelScope.launch {
            achievementRepository.observeEarned(userId).collect { achievements ->
                _state.update {
                    it.copy(
                        recentAchievements = achievements
                            .sortedByDescending { a -> a.earnedAt?.time ?: 0 }
                            .take(3),
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Повторно отправляет письмо с подтверждением почты.
     * Требует, чтобы пользователь был залогинен в FirebaseAuth.
     */
    fun resendVerificationEmail() {
        viewModelScope.launch {
            _state.update {
                it.copy(resendingVerification = true, verificationSentMessage = null)
            }
            try {
                val authUser = FirebaseAuth.getInstance().currentUser
                if (authUser == null) {
                    _state.update {
                        it.copy(
                            resendingVerification = false,
                            verificationSentMessage = "Сессия истекла. Войдите заново."
                        )
                    }
                    return@launch
                }
                authUser.sendEmailVerification().await()
                _state.update {
                    it.copy(
                        resendingVerification = false,
                        verificationSentMessage = "Письмо отправлено на ${authUser.email}"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        resendingVerification = false,
                        verificationSentMessage = "Ошибка: ${e.message ?: "не удалось отправить"}"
                    )
                }
            }
        }
    }

    fun clearVerificationMessage() {
        _state.update { it.copy(verificationSentMessage = null) }
    }
}"""),
])

print()
print("Готово.")
print("Пересобери APK: если у teen emailVerified == false, в TeenHome появится")
print("красный баннер 'Почта не подтверждена' с кнопкой 'Отправить повторно'.")