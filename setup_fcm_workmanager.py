#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub — ускорение ChatSyncWorker до 60 секунд.
Само-перезапускающаяся OneTimeWorkRequest вместо Periodic (15 мин min).
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


# ============================================================
# Новый ChatSyncWorker.kt
# ============================================================
CHAT_WORKER = '''package com.pathfinder.hub.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pathfinder.hub.data.local.LastSeenStore
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.ChatRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.notifications.ChatNotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Проверяет Firestore на новые сообщения в DM и групповых чатах.
 * Показывает локальные уведомления.
 *
 * Вместо PeriodicWorkRequest (минимум 15 мин) используем
 * OneTimeWorkRequest, который в конце сам планирует следующую итерацию
 * через [INTERVAL_SECONDS] секунд. Это даёт интервал 60 секунд.
 *
 * Firestore reads: ~1 чтение на DM/группу за итерацию. При 10 чатах
 * и интервале 60 секунд — ~14 400 чтений/день, укладывается в free tier.
 */
@HiltWorker
class ChatSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val chatRepo: ChatRepository,
    private val lastSeen: LastSeenStore,
    private val notifier: ChatNotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val uid = sessionManager.getUserId()
            if (uid.isNullOrBlank()) {
                Log.d(TAG, "no user — skip")
            } else {
                val user = try { userRepository.getUser(uid) } catch (_: Exception) { null }
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    Log.d(TAG, "no club — skip")
                } else {
                    checkDms(clubId, uid)
                    checkGroups(clubId, uid)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "doWork error", e)
        } finally {
            // В любом случае планируем следующую итерацию
            scheduleNext(applicationContext, INTERVAL_SECONDS)
        }
        return Result.success()
    }

    private suspend fun checkDms(clubId: String, uid: String) {
        val dms = try { chatRepo.getMyDmsOnce(clubId, uid) } catch (_: Exception) { emptyList() }
        for (dm in dms) {
            val key = lastSeen.dmKey(dm.id)
            val since = lastSeen.get(key)

            // Первый раз — запоминаем «сейчас», не показываем старое
            if (since == 0L) {
                lastSeen.set(key, System.currentTimeMillis())
                continue
            }

            // Дёшево: сравниваем lastMessageAt из мета-документа,
            // без запроса к messages
            val lastAt = dm.lastMessageAt?.time ?: continue
            if (lastAt <= since) continue

            // Есть что-то новое — берём сообщения
            val newMessages = try {
                chatRepo.getDmMessagesSince(clubId, dm.id, since)
            } catch (_: Exception) { emptyList() }

            val fromOthers = newMessages.filter { it.authorId != uid }
            if (fromOthers.isNotEmpty()) {
                val other = chatRepo.otherParticipant(dm, uid)
                val otherUser = if (other.isNotBlank()) userRepository.getUser(other) else null
                val senderName = otherUser?.let { "${it.firstName} ${it.lastName}".trim() }
                    ?.takeIf { it.isNotBlank() } ?: "Новое сообщение"
                val last = fromOthers.last()
                notifier.notifyDm(
                    dmId = dm.id,
                    otherUid = other,
                    title = senderName,
                    body = last.text.take(100).ifBlank { "Вложение" },
                )
                Log.d(TAG, "notified DM ${dm.id}")
            }
            lastSeen.set(key, lastAt)
        }
    }

    private suspend fun checkGroups(clubId: String, uid: String) {
        val groups = try { chatRepo.getMyGroupsOnce(clubId, uid) } catch (_: Exception) { emptyList() }
        for (g in groups) {
            val key = lastSeen.groupKey(g.id)
            val since = lastSeen.get(key)

            if (since == 0L) {
                lastSeen.set(key, System.currentTimeMillis())
                continue
            }

            val lastAt = g.lastMessageAt?.time ?: continue
            if (lastAt <= since) continue

            val newMessages = try {
                chatRepo.getGroupMessagesSince(clubId, g.id, since)
            } catch (_: Exception) { emptyList() }

            val fromOthers = newMessages.filter { it.authorId != uid }
            if (fromOthers.isNotEmpty()) {
                val last = fromOthers.last()
                val author = userRepository.getUser(last.authorId)
                val authorName = author?.let { "${it.firstName} ${it.lastName}".trim() }
                    ?.takeIf { it.isNotBlank() } ?: "Сообщение"
                notifier.notifyGroup(
                    groupId = g.id,
                    title = "${g.name} — $authorName",
                    body = last.text.take(100).ifBlank { "Вложение" },
                )
                Log.d(TAG, "notified group ${g.id}")
            }
            lastSeen.set(key, lastAt)
        }
    }

    companion object {
        private const val TAG = "ChatSyncWorker"

        /** Имя уникальной работы в WorkManager. */
        const val WORK_NAME = "chat_sync_realtime"

        /** Интервал между итерациями, секунды. */
        const val INTERVAL_SECONDS = 60L

        /**
         * Планирует следующую итерацию через [delaySeconds].
         * Если задача с таким именем уже есть — заменяет её.
         */
        fun scheduleNext(ctx: Context, delaySeconds: Long) {
            val request = OneTimeWorkRequestBuilder<ChatSyncWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(ctx).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
'''


# ============================================================
# Патч PathfinderApp
# ============================================================
def patch_app() -> bool:
    print("\n🔧 Патчу PathfinderApp.kt...")
    f = PKG / "PathfinderApp.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    # Заменяем старый scheduleChatSync (Periodic) на новый (OneTime)
    old_method = '''    private fun scheduleChatSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ChatSyncWorker>(
            30, TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ChatSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Log.d(TAG, "ChatSyncWorker поставлен в очередь (каждые 30 мин)")
    }'''

    new_method = '''    private fun scheduleChatSync() {
        // Первый запуск через 5 секунд после старта приложения,
        // дальше ChatSyncWorker сам себя перезапускает каждые 60 секунд.
        ChatSyncWorker.scheduleNext(this, 5L)
        Log.d(TAG, "ChatSyncWorker: первая проверка через 5 сек, интервал 60 сек")
    }'''

    if old_method not in text:
        print("❌ Не найден старый метод scheduleChatSync (Periodic)")
        print("   Возможно, ты ещё не запускал setup_workmanager_notifications.py.")
        return False

    text = text.replace(old_method, new_method, 1)

    # Убираем неиспользуемые импорты Periodic / ExistingPeriodic — не критично, оставим
    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print("✅ PathfinderApp.kt пропатчен")
    return True


# ============================================================
# MAIN
# ============================================================
def main():
    print("=" * 60)
    print("  Ускорение ChatSyncWorker (60 сек)")
    print("=" * 60)

    # 1. Перезаписываем ChatSyncWorker.kt
    f = PKG / "data/sync/ChatSyncWorker.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        print("   Сначала запусти setup_workmanager_notifications.py")
        sys.exit(1)
    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(CHAT_WORKER, encoding="utf-8")
    print(f"✅ ChatSyncWorker.kt перезаписан")

    # 2. Патчим PathfinderApp
    ok = patch_app()

    print("\n" + "=" * 60)
    if ok:
        print("✅ Готово.")
        print("\nСборка: .\\gradlew assembleDebug")
        print("\nТеперь:")
        print("  - Первая проверка через 5 сек после старта")
        print("  - Далее каждые 60 сек, воркер сам себя перезапускает")
        print("  - В логе ищи тег ChatSyncWorker")
    else:
        print("❌ Патч не удался.")
    print("=" * 60)


if __name__ == "__main__":
    main()