package com.pathfinder.hub

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.ui.navigation.PathfinderNavHost
import com.pathfinder.hub.ui.navigation.Routes
import com.pathfinder.hub.ui.theme.PathfinderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Проверяем локальную сессию — БЕЗ Firebase и Firestore.
        // Если пользователь уже входил, сразу пускаем на главный экран.
        lifecycleScope.launch {
            val startDestination = resolveStartDestination()

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

    /**
     * Определяем стартовый экран:
     *  - если есть sessionUserId И в Room есть UserEntity → ROLE_HOME
     *  - иначе → LOGIN
     *
     * Работает без интернета: только SessionManager + Room.
     */
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

        Log.d(TAG, "Автологин: userId=$userId, role=${localUser.role}")
        return Routes.ROLE_HOME
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}