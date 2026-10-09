#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub — FCM (clean).
Создаёт 3 файла + патчит 3. Без ChatSyncWorker (не нужен, есть Cloud Function).
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
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"

if not PKG.exists():
    print(f"❌ Не найден пакет: {PKG}")
    sys.exit(1)


FILES = {}

# =========================================================================
# 1. FcmTokenRepository
# =========================================================================
FILES["data/repository/FcmTokenRepository.kt"] = '''package com.pathfinder.hub.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохраняет FCM-токены в Firestore.
 * Путь: users/{uid}/fcmTokens/{token}
 */
@Singleton
class FcmTokenRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun registerCurrentToken(userId: String): Result<String> = try {
        val token = FirebaseMessaging.getInstance().token.await()
        if (token.isBlank()) return Result.failure(IllegalStateException("Пустой FCM-токен"))
        saveToken(userId, token)
        Result.success(token)
    } catch (e: Exception) {
        Log.e(TAG, "registerCurrentToken failed", e)
        Result.failure(e)
    }

    suspend fun saveToken(userId: String, token: String): Result<Unit> = try {
        val data = mapOf(
            "userId" to userId,
            "token" to token,
            "platform" to "android",
            "deviceId" to android.os.Build.MODEL,
            "updatedAt" to Timestamp(Date()),
        )
        firestore.collection("users")
            .document(userId)
            .collection("fcmTokens")
            .document(token)
            .set(data)
            .await()
        Log.d(TAG, "FCM token saved: ${token.take(12)}…")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "saveToken failed", e)
        Result.failure(e)
    }

    companion object {
        private const val TAG = "FcmTokenRepo"
    }
}
'''

# =========================================================================
# 2. ChatNotificationHelper
# =========================================================================
FILES["notifications/ChatNotificationHelper.kt"] = '''package com.pathfinder.hub.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pathfinder.hub.MainActivity
import com.pathfinder.hub.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Показывает локальные уведомления о новых сообщениях в чатах.
 * Тап на уведомление → MainActivity с extra-полями → PathfinderNavHost.
 */
@Singleton
class ChatNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val CHANNEL_ID = "pathfinder_chats"
        const val CHANNEL_NAME = "Сообщения"
        const val CHANNEL_DESC = "Уведомления о новых сообщениях в чатах"

        const val EXTRA_TYPE = "deeplink_type"
        const val EXTRA_TARGET_ID = "deeplink_target_id"
        const val EXTRA_OTHER_UID = "deeplink_other_uid"
    }

    private val manager = NotificationManagerCompat.from(context)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = CHANNEL_DESC }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun notifyDm(dmId: String, otherUid: String, title: String, body: String) {
        show(dmId.hashCode(), title, body, buildIntent("dm", dmId, otherUid))
    }

    fun notifyGroup(groupId: String, title: String, body: String) {
        show(groupId.hashCode(), title, body, buildIntent("group", groupId, null))
    }

    fun notifyClubChat(clubId: String, title: String, body: String) {
        show(clubId.hashCode(), title, body, buildIntent("club_chat", clubId, null))
    }

    private fun buildIntent(type: String, targetId: String, otherUid: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_TARGET_ID, targetId)
            if (otherUid != null) putExtra(EXTRA_OTHER_UID, otherUid)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, targetId.hashCode(), intent, flags)
    }

    private fun show(id: Int, title: String, body: String, intent: PendingIntent) {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Разрешение не выдано — игнорируем
        }
    }
}
'''

# =========================================================================
# 3. PathfinderMessagingService
# =========================================================================
FILES["notifications/PathfinderMessagingService.kt"] = '''package com.pathfinder.hub.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.FcmTokenRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Принимает push-уведомления от FCM (наша Yandex Cloud Function).
 */
@AndroidEntryPoint
class PathfinderMessagingService : FirebaseMessagingService() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var tokenRepo: FcmTokenRepository
    @Inject lateinit var notifier: ChatNotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(12)}…")
        val uid = sessionManager.getUserId() ?: return
        scope.launch { tokenRepo.saveToken(uid, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Push: from=${message.from}, data=${message.data}")

        val data = message.data
        val type = data["type"] ?: "dm"
        val targetId = data["targetId"] ?: ""
        val otherUid = data["otherUid"] ?: ""
        val title = message.notification?.title ?: data["title"] ?: "Новое сообщение"
        val body = message.notification?.body ?: data["body"] ?: ""

        when (type) {
            "dm" -> notifier.notifyDm(targetId, otherUid, title, body)
            "group" -> notifier.notifyGroup(targetId, title, body)
            "club_chat" -> notifier.notifyClubChat(targetId, title, body)
            else -> Log.w(TAG, "Unknown push type: $type")
        }
    }

    companion object {
        private const val TAG = "PathfinderFCM"
    }
}
'''


def write_files() -> None:
    print(f"📁 Пакет: {PKG}\n")
    for rel, content in FILES.items():
        target = PKG / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            shutil.copy2(target, target.with_suffix(target.suffix + ".bak"))
            print(f"⚠️  Перезаписываю {rel}")
        target.write_text(content, encoding="utf-8")
        print(f"✅ {rel}")


# ============================================================
# ПАТЧ 1 — MainActivity.kt (полная замена)
# ============================================================
def patch_main_activity() -> bool:
    print("\n🔧 Патчу MainActivity.kt...")
    f = PKG / "MainActivity.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    shutil.copy2(f, f.with_suffix(".kt.bak"))

    new_content = '''package com.pathfinder.hub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.FcmTokenRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.notifications.ChatNotificationHelper
import com.pathfinder.hub.ui.navigation.PathfinderNavHost
import com.pathfinder.hub.ui.navigation.Routes
import com.pathfinder.hub.ui.theme.PathfinderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var fcmTokenRepository: FcmTokenRepository

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> Log.d(TAG, "POST_NOTIFICATIONS granted=$granted") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleDeepLink(intent)
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent ?: return
        val type = intent.getStringExtra(ChatNotificationHelper.EXTRA_TYPE) ?: return
        val targetId = intent.getStringExtra(ChatNotificationHelper.EXTRA_TARGET_ID) ?: return
        val otherUid = intent.getStringExtra(ChatNotificationHelper.EXTRA_OTHER_UID)
        Log.d(TAG, "Deep link: type=$type, targetId=$targetId, otherUid=$otherUid")
        _deepLinks.tryEmit(ChatDeepLink(type, targetId, otherUid))
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private suspend fun registerFcmTokenIfLoggedIn() {
        val uid = sessionManager.getUserId() ?: return
        val r = fcmTokenRepository.registerCurrentToken(uid)
        r.fold(
            onSuccess = { token -> Log.d(TAG, "FCM registered: ${token.take(12)}…") },
            onFailure = { e -> Log.w(TAG, "FCM register failed: ${e.message}") },
        )
    }

    private suspend fun resolveStartDestination(): String {
        val userId = sessionManager.getUserId()
        if (userId.isNullOrBlank()) {
            Log.d(TAG, "Нет sessionUserId — стартуем с LOGIN")
            return Routes.LOGIN
        }
        val localUser = userRepository.getUserLocalOnly(userId)
        if (localUser == null) {
            Log.w(TAG, "sessionUserId=$userId, но UserEntity в Room нет — стартуем с LOGIN")
            return Routes.LOGIN
        }
        if (!localUser.onboardingCompleted) {
            Log.d(TAG, "Онбординг не завершён — ведём на ONBOARDING")
            return Routes.onboarding(localUser.role, userId)
        }
        Log.d(TAG, "Автологин: userId=$userId, role=${localUser.role}")
        return Routes.ROLE_HOME
    }

    data class ChatDeepLink(
        val type: String,
        val targetId: String,
        val otherUid: String?,
    )

    companion object {
        private const val TAG = "MainActivity"

        private val _deepLinks = MutableSharedFlow<ChatDeepLink>(extraBufferCapacity = 4)
        val deepLinks = _deepLinks.asSharedFlow()
    }
}
'''
    f.write_text(new_content, encoding="utf-8")
    print("✅ MainActivity.kt переписан")
    return True


# ============================================================
# ПАТЧ 2 — PathfinderNavHost.kt (подписка на deep links)
# ============================================================
def patch_navhost() -> bool:
    print("\n🔧 Патчу PathfinderNavHost.kt...")
    f = PKG / "ui/navigation/PathfinderNavHost.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "MainActivity.deepLinks" in text:
        print("⚠️  Уже пропатчен")
        return True

    # Импорты
    imp_anchor = "import androidx.compose.runtime.Composable"
    if imp_anchor not in text:
        print("❌ Нет якоря Composable import")
        return False
    text = text.replace(
        imp_anchor,
        imp_anchor + "\nimport androidx.compose.runtime.LaunchedEffect"
        + "\nimport com.pathfinder.hub.MainActivity",
        1,
    )

    # LaunchedEffect перед NavHost
    hook = '''    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {'''
    if hook not in text:
        print("❌ Не найден якорь NavHost")
        return False

    block = '''    LaunchedEffect(Unit) {
        MainActivity.deepLinks.collect { link ->
            when (link.type) {
                "dm" -> {
                    val other = link.otherUid ?: ""
                    navController.navigate(Routes.dmChat(link.targetId, other))
                }
                "group" -> navController.navigate(Routes.customGroupChat(link.targetId))
                "club_chat" -> navController.navigate(Routes.CLUB_CHAT)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {'''
    text = text.replace(hook, block, 1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print("✅ PathfinderNavHost.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 3 — AndroidManifest.xml (регистрация сервиса)
# ============================================================
def patch_manifest() -> bool:
    print("\n🔧 Патчу AndroidManifest.xml...")
    if not MANIFEST.exists():
        print(f"❌ Не найден {MANIFEST}")
        return False

    text = MANIFEST.read_text(encoding="utf-8")
    if "PathfinderMessagingService" in text:
        print("⚠️  Уже пропатчен")
        return True

    anchor = "    </application>"
    if anchor not in text:
        print("❌ Не найден </application>")
        return False

    block = '''        <!-- FCM: приём push-уведомлений -->
        <service
            android:name="com.pathfinder.hub.notifications.PathfinderMessagingService"
            android:exported="false"
            android:directBootAware="true" >
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>

        <!-- Канал уведомлений по умолчанию -->
        <meta-data
            android:name="com.google.firebase.messaging.default_notification_channel_id"
            android:value="pathfinder_chats" />

    </application>'''
    text = text.replace(anchor, block, 1)

    shutil.copy2(MANIFEST, MANIFEST.with_suffix(".xml.bak"))
    MANIFEST.write_text(text, encoding="utf-8")
    print("✅ AndroidManifest.xml пропатчен")
    return True


def main():
    print("=" * 60)
    print("  Pathfinder Hub — FCM clean")
    print("=" * 60)

    write_files()

    r = {
        "MainActivity.kt":      patch_main_activity(),
        "PathfinderNavHost.kt": patch_navhost(),
        "AndroidManifest.xml":  patch_manifest(),
    }

    print("\n" + "=" * 60)
    for k, v in r.items():
        print(f"  {'✅' if v else '❌'} {k}")

    if all(r.values()):
        print("\n✅ Готово.")
        print("\nСборка: .\\gradlew assembleDebug")
        print("\nТест:")
        print("  1. Установи, войди под юзером")
        print("  2. В логах Android: FCM registered: dXyz…")
        print("  3. Firebase Console → users/{uid}/fcmTokens — токен появился")
        print("  4. Юзер А пишет DM юзеру Б")
        print("  5. Через 1 мин у Юзера Б появляется push")
    else:
        print("\n⚠️  Смотри .bak.")
    print("=" * 60)


if __name__ == "__main__":
    main()