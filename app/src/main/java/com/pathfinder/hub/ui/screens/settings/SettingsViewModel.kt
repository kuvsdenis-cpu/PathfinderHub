package com.pathfinder.hub.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.dao.NotificationDao
import com.pathfinder.hub.data.local.entity.notifications.NotificationPreferencesEntity
import com.pathfinder.hub.data.local.entity.settings.UserSettingsEntity
import com.pathfinder.hub.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val theme: String = "system",
    val prefs: NotificationPreferencesEntity? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notificationDao: NotificationDao,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val userId: String? = sessionManager.getUserId()

    init {
        val uid = userId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не авторизован") }
        } else {
            viewModelScope.launch {
                settingsRepository.observeUserSettings(uid).collect { s ->
                    _state.update { it.copy(theme = s?.theme ?: "system") }
                }
            }
            viewModelScope.launch {
                notificationDao.observePrefs(uid).collect { p ->
                    _state.update { it.copy(prefs = p, isLoading = false) }
                }
            }
        }
    }

    fun onThemeChange(theme: String) {
        val uid = userId ?: return
        viewModelScope.launch {
            val current = settingsRepository.observeUserSettings(uid).first()
            val base = current
                ?: UserSettingsEntity(userId = uid, theme = theme, updatedAt = Date())
            settingsRepository.upsertUserSettings(
                base.copy(theme = theme, updatedAt = Date())
            )
        }
    }

    private suspend fun currentPrefs(): NotificationPreferencesEntity {
        val uid = userId ?: error("no user")
        return notificationDao.observePrefs(uid).first()
            ?: NotificationPreferencesEntity(
                userId = uid,
                quietHoursEnabled = false,
                quietHoursStart = 22,
                quietHoursEnd = 8,
                dailyPushLimit = 100,
                pushEnabled = true,
                emailEnabled = false,
                digestEnabled = false,
                digestDay = "sunday",
                digestTime = "09:00",
                categoryTasks = true,
                categoryModeration = true,
                categoryLearning = true,
                categoryGamification = true,
                categoryEvents = true,
                categoryConsent = true,
                categoryProgress = true,
                categoryPayments = true,
                categoryContact = true,
            )
    }

    fun onPushToggle(enabled: Boolean) {
        viewModelScope.launch {
            notificationDao.upsertPrefs(currentPrefs().copy(pushEnabled = enabled))
        }
    }

    fun onEmailToggle(enabled: Boolean) {
        viewModelScope.launch {
            notificationDao.upsertPrefs(currentPrefs().copy(emailEnabled = enabled))
        }
    }

    fun onQuietHoursToggle(enabled: Boolean) {
        viewModelScope.launch {
            notificationDao.upsertPrefs(currentPrefs().copy(quietHoursEnabled = enabled))
        }
    }

    fun onCategoryToggle(category: String, enabled: Boolean) {
        viewModelScope.launch {
            val cur = currentPrefs()
            val p = when (category) {
                "tasks" -> cur.copy(categoryTasks = enabled)
                "moderation" -> cur.copy(categoryModeration = enabled)
                "learning" -> cur.copy(categoryLearning = enabled)
                "gamification" -> cur.copy(categoryGamification = enabled)
                "events" -> cur.copy(categoryEvents = enabled)
                "consent" -> cur.copy(categoryConsent = enabled)
                "progress" -> cur.copy(categoryProgress = enabled)
                "payments" -> cur.copy(categoryPayments = enabled)
                "contact" -> cur.copy(categoryContact = enabled)
                else -> cur
            }
            notificationDao.upsertPrefs(p)
        }
    }
}
