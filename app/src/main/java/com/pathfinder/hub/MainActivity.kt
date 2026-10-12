package com.pathfinder.hub

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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.session.SessionGuard
import com.pathfinder.hub.data.repository.FcmTokenRepository
import com.pathfinder.hub.data.repository.SettingsRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.notifications.ChatNotificationHelper
import com.pathfinder.hub.ui.navigation.PathfinderNavHost
import com.pathfinder.hub.ui.navigation.Routes
import com.pathfinder.hub.ui.theme.PathfinderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var fcmTokenRepository: FcmTokenRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sessionGuard: SessionGuard

    private val uiReady = androidx.compose.runtime.mutableStateOf(false)

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> Log.d(TAG, "POST_NOTIFICATIONS granted=$granted") }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !uiReady.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleDeepLink(intent)
        askNotificationPermission()

        // UI стартует СРАЗУ (офлайн-автологин). resolveStartDestination() читает Room.
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
            }
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
                        // #7: держим сплэш 1200 мс, пока первый кадр
                        // DirectorHomeScreen не готов.
                        delay(1200)
                        uiReady.value = true
                    }

                    // Пока Room ещё читается, показываем ROUTE_HOME — RoleRouter
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
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Регистрируем FCM-токен при каждой активации activity.
        // Это нужно, чтобы после логина под новым пользователем
        // токен сохранился в users/{uid}/fcmTokens/{token}.
        lifecycleScope.launch {
            registerFcmTokenIfLoggedIn()
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
        // #7: роут по роли — сразу целевой экран, без двойного прохода
        // через RoleHomeScreen. RoleHomeScreen остаётся для logout→login.
        val dest = when (localUser.role) {
            "director" -> Routes.DIRECTOR_HOME
            "instructor" -> Routes.INSTRUCTOR_HOME
            "secretary" -> Routes.SECRETARY_HOME
            "parent" -> Routes.PARENT_HOME
            "conference_admin" -> Routes.CONFERENCE_HOME
            "conference_secretary" -> Routes.CONFERENCE_SECRETARY_HOME
            "teen" -> Routes.TEEN_HOME
            else -> Routes.TEEN_HOME
        }
        Log.d(TAG, "Автологин: userId=$userId, role=${localUser.role}, dest=$dest")
        return dest
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