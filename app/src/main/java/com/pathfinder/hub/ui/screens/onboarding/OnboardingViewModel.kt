package com.pathfinder.hub.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.domain.usecase.auth.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingSlide(
    val title: String,
    val text: String
)

data class OnboardingUiState(
    val role: String = "teen",
    val currentSlide: Int = 0,
    val totalSlides: Int = 0,
    val isFinished: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboardingUseCase: CompleteOnboardingUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setRole(role: String) {
        val slides = OnboardingContent.getSlides(role)
        _state.update {
            it.copy(role = role, totalSlides = slides.size, currentSlide = 0)
        }
    }

    fun next() {
        val s = _state.value
        if (s.currentSlide < s.totalSlides - 1) {
            _state.update { it.copy(currentSlide = it.currentSlide + 1) }
        }
    }

    fun finish(userId: String) {
        viewModelScope.launch {
            completeOnboardingUseCase(userId)
            _state.update { it.copy(isFinished = true) }
        }
    }
}

/**
 * Контент онбординга вынесен в отдельный объект,
 * чтобы снизить сложность ViewModel (SonarQube).
 */
object OnboardingContent {

    fun getSlides(role: String): List<OnboardingSlide> = when (role) {
        "teen" -> teenSlides()
        "parent" -> parentSlides()
        "instructor" -> instructorSlides()
        "director" -> directorSlides()
        "secretary" -> secretarySlides()
        "conference" -> conferenceSlides()
        else -> emptyList()
    }

    private fun teenSlides() = listOf(
        OnboardingSlide(
            "Добро пожаловать в клуб!",
            "«Следопыт» — это клуб для подростков 10–15 лет, где ты растёшь духовно и физически."
        ),
        OnboardingSlide(
            "Твой прогресс",
            "Проходи ступени и сдавай специализации — от «Друга» до «Проводника»."
        ),
        OnboardingSlide(
            "Цифровая форма",
            "Нашивки за специализации появятся на твоём виртуальном аватаре."
        ),
        OnboardingSlide(
            "События и задачи",
            "Смотри расписание, отмечайся на занятиях и выполняй задания наставника."
        ),
        OnboardingSlide(
            "Готов начать?",
            "Нажми «Поехали» — и вперёд к первым достижениям!"
        )
    )

    private fun parentSlides() = listOf(
        OnboardingSlide(
            "Что видит ваш ребёнок",
            "Ребёнок видит свой прогресс, специализации и достижения в приложении."
        ),
        OnboardingSlide(
            "Ваш доступ",
            "Вы видите расписание, прогресс и оплату. Вы не видите чат и личные заметки наставника."
        ),
        OnboardingSlide(
            "Приватность",
            "Все данные защищены. Мы следуем законам о защите персональных данных несовершеннолетних."
        )
    )

    private fun instructorSlides() = listOf(
        OnboardingSlide(
            "Ваша роль",
            "Вы работаете с детьми: проводите занятия, проверяете практику, ведёте чек-листы."
        ),
        OnboardingSlide(
            "Занятия и задачи",
            "Планируйте занятия и назначайте задачи своим подопечным."
        ),
        OnboardingSlide(
            "Проверка сдач",
            "Отмечайте выполнение требований и специализаций."
        ),
        OnboardingSlide(
            "Модерация",
            "Одобряйте или скрывайте контент клуба."
        )
    )

    private fun directorSlides() = listOf(
        OnboardingSlide(
            "Управление клубом",
            "Вы управляете клубом: участники, расписание, отчёты."
        ),
        OnboardingSlide(
            "Планирование",
            "Создавайте события и задачи для наставников."
        ),
        OnboardingSlide(
            "Модерация",
            "Проверяйте контент и эскалируйте апелляции."
        ),
        OnboardingSlide(
            "Отчёты",
            "Генерируйте отчёты для конференции в один клик."
        ),
        OnboardingSlide(
            "Команда",
            "Назначайте наставников и секретарей."
        )
    )

    private fun secretarySlides() = listOf(
        OnboardingSlide(
            "Посещаемость",
            "Ведите электронный журнал посещаемости."
        ),
        OnboardingSlide(
            "Модерация",
            "Помогайте директору проверять контент."
        ),
        OnboardingSlide(
            "Отчёты",
            "Готовьте отчёты и выгружайте данные."
        )
    )

    private fun conferenceSlides() = listOf(
        OnboardingSlide(
            "Обзор конференции",
            "Вы видите все клубы, лидеров и статистику."
        ),
        OnboardingSlide(
            "Модерация",
            "Вы — вторая и третья инстанция апелляций."
        ),
        OnboardingSlide(
            "Аналитика",
            "Сводки по клубам: посещаемость, прогресс, специализации."
        ),
        OnboardingSlide(
            "Отчёты",
            "Экспорт отчётов по конференции."
        )
    )
}