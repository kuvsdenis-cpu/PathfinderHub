package com.pathfinder.hub.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.domain.usecase.auth.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingSlide(val title: String, val text: String)

data class OnboardingUiState(
    val role: String = "teen",
    val currentSlide: Int = 0,
    val totalSlides: Int = 0,
    val isFinished: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val sessionManager: SessionManager // <-- ДОБАВЛЕНО
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setRole(role: String) {
        val slides = OnboardingContent.getSlides(role)
        _state.update { it.copy(role = role, totalSlides = slides.size, currentSlide = 0) }
    }

    fun next() {
        val s = _state.value
        if (s.currentSlide < s.totalSlides - 1) {
            _state.update { it.copy(currentSlide = it.currentSlide + 1) }
        }
    }

    fun finish(userId: String) {
        viewModelScope.launch {
            // Если userId пустой (как при вызове из NavHost), берем из SessionManager
            val actualUserId = userId.ifBlank { sessionManager.getUserId() ?: "" }

            if (actualUserId.isNotBlank()) {
                // На всякий случай сохраняем его явно
                sessionManager.saveUserId(actualUserId)
                completeOnboardingUseCase(actualUserId)
            }
            _state.update { it.copy(isFinished = true) }
        }
    }
}

object OnboardingContent {
    fun getSlides(role: String): List<OnboardingSlide> = when (role) {
        "teen" -> listOf(
            OnboardingSlide("Добро пожаловать!", "«Следопыт» — это клуб для подростков, где ты растёшь духовно и физически."),
            OnboardingSlide("Твой прогресс", "Проходи ступени и сдавай специализации."),
            OnboardingSlide("Готов начать?", "Нажми «Поехали» — и вперёд к первым достижениям!")
        )
        "director" -> listOf(
            OnboardingSlide("Управление клубом", "Вы управляете клубом: участники, расписание, отчёты."),
            OnboardingSlide("Готовы?", "Нажмите «Поехали», чтобы начать работу.")
        )
        else -> listOf(OnboardingSlide("Добро пожаловать", "Приложение Pathfinder Hub"))
    }
}