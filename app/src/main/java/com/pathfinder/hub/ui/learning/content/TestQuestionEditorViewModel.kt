package com.pathfinder.hub.ui.learning.content

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.repository.TestRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class QuestionDraft(
    val id: String,
    val text: String,
    val type: String,
    val options: List<String>,
    val correctAnswer: String,
    val points: Int,
)

data class TestQuestionEditorUiState(
    val isLoading: Boolean = true,
    val testId: String = "",
    val questions: List<QuestionDraft> = emptyList(),
    val editingQuestion: QuestionDraft? = null,
    val pendingDeleteId: String? = null,
    val errorMessage: String? = null,
    val canEdit: Boolean = false,
)

@HiltViewModel
class TestQuestionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val testRepository: TestRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val testId: String = savedStateHandle.get<String>("testId") ?: ""

    private val _state = MutableStateFlow(TestQuestionEditorUiState(testId = testId))
    val state: StateFlow<TestQuestionEditorUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                if (testId.isBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "testId не задан") }
                    return@launch
                }

                val userId = sessionManager.getUserId()
                val user = userId?.let { userRepository.getUser(it) }
                val canEdit = user != null && (
                    PermissionChecker.hasPerm(user, "content.edit")
                    || user.role == "instructor"
                )
                if (!canEdit) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            canEdit = false,
                            errorMessage = "Нет доступа к редактору вопросов",
                        )
                    }
                    return@launch
                }

                val entities = testRepository.getQuestionsForTest(testId)
                val drafts = entities.map {
                    QuestionDraft(
                        id = it.id,
                        text = it.text,
                        type = it.type,
                        options = it.options,
                        correctAnswer = it.correctAnswer,
                        points = it.points,
                    )
                }
                _state.update { it.copy(isLoading = false, canEdit = true, questions = drafts) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    fun createQuestion() {
        _state.update {
            it.copy(editingQuestion = QuestionDraft(
                id = "",
                text = "",
                type = "choice",
                options = emptyList(),
                correctAnswer = "",
                points = 1,
            ))
        }
    }

    fun editQuestion(q: QuestionDraft) {
        _state.update { it.copy(editingQuestion = q) }
    }

    fun cancelEdit() {
        _state.update { it.copy(editingQuestion = null) }
    }

    fun saveQuestion(draft: QuestionDraft) {
        val current = _state.value.questions.toMutableList()
        val isNew = draft.id.isBlank()
        val finalDraft = if (isNew) draft.copy(id = UUID.randomUUID().toString()) else draft

        if (isNew) {
            current.add(finalDraft)
        } else {
            val idx = current.indexOfFirst { it.id == finalDraft.id }
            if (idx >= 0) current[idx] = finalDraft else current.add(finalDraft)
        }

        _state.update { it.copy(questions = current, editingQuestion = null) }
        persist()
    }

    fun askDelete(id: String) = _state.update { it.copy(pendingDeleteId = id) }
    fun cancelDelete() = _state.update { it.copy(pendingDeleteId = null) }

    fun confirmDelete() {
        val id = _state.value.pendingDeleteId ?: return
        val next = _state.value.questions.filterNot { it.id == id }
        _state.update { it.copy(questions = next, pendingDeleteId = null) }
        persist()
    }

    private fun persist() {
        if (!_state.value.canEdit) return
        val drafts = _state.value.questions
        viewModelScope.launch {
            try {
                val entities = drafts.mapIndexed { idx, d ->
                    TestQuestionEntity(
                        id = d.id,
                        testId = testId,
                        clubId = null, // будет проставлено в TestRepository.replaceQuestions по clubId теста
                        honorId = "",
                        type = d.type,
                        text = d.text,
                        options = d.options,
                        correctAnswer = d.correctAnswer,
                        points = d.points,
                        order = idx,
                    )
                }
                testRepository.replaceQuestions(testId, entities)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка сохранения") }
            }
        }
    }
}
