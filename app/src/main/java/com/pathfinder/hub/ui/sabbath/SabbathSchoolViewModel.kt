package com.pathfinder.hub.ui.sabbath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.remote.dto.sabbath.SabbathLessonDto
import com.pathfinder.hub.data.repository.SabbathSchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SabbathUiState(
    val isLoading: Boolean = true,
    val lesson: SabbathLessonDto? = null,
    val answers: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
)

@HiltViewModel
class SabbathSchoolViewModel @Inject constructor(
    private val repository: SabbathSchoolRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SabbathUiState())
    val state = _state.asStateFlow()

    // #17: userId не в конструкторе — SessionManager может быть не готов.
    private var cachedUserId: String? = null

    private suspend fun resolveUserId(): String? {
        cachedUserId?.let { return it }
        repeat(30) {
            val uid = sessionManager.getUserId()
            if (!uid.isNullOrBlank()) {
                cachedUserId = uid
                return uid
            }
            delay(100)
        }
        return null
    }

    fun loadLesson(lessonId: String) {
        viewModelScope.launch {
            val uid = resolveUserId()
            if (uid == null) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = "Пользователь не авторизован")
                }
                return@launch
            }
            val lesson = repository.loadLesson(lessonId)
            repository.observeAnswers(uid, lessonId).collect { savedAnswers ->
                val answersMap = savedAnswers.associate { it.blockId to it.answerText }
                _state.update {
                    it.copy(
                        isLoading = false,
                        lesson = lesson,
                        answers = answersMap,
                        errorMessage = null,
                    )
                }
            }
        }
    }

    fun onAnswerChanged(lessonId: String, blockId: String, text: String) {
        viewModelScope.launch {
            val uid = resolveUserId() ?: return@launch
            repository.saveAnswer(uid, lessonId, blockId, text)
        }
    }
}
