package com.pathfinder.hub.ui.learning.content

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.TestEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.BookRepository
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.QrQuestRepository
import com.pathfinder.hub.data.repository.TaskRepository
import com.pathfinder.hub.data.repository.TestRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TestEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isNew: Boolean = true,
    val testId: String = "",
    val clubId: String? = null,

    // Поля формы
    val title: String = "",
    val description: String = "",
    val scope: String = "standalone",
    val scopeId: String? = null,
    val passScoreText: String = "90",

    // Опции для dropdown scopeId
    val scopeIdOptions: List<Pair<String, String>> = emptyList(),

    // Прочее
    val questionCount: Int = 0,
    val showDeleteDialog: Boolean = false,
    val errorMessage: String? = null,
    /** Может ли текущий пользователь редактировать тесты. */
    val canEdit: Boolean = false,
)

@HiltViewModel
class TestEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val testRepository: TestRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val honorRepository: HonorRepository,
    private val levelRepository: LevelRepository,
    private val taskRepository: TaskRepository,
    private val qrQuestRepository: QrQuestRepository,
    private val eventRepository: EventRepository,
    private val achievementRepository: AchievementRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val testIdArg: String = savedStateHandle.get<String>("testId") ?: "new"
    private val prefilledScope: String? = savedStateHandle.get<String>("scope")
    private val prefilledScopeId: String? = savedStateHandle.get<String>("scopeId")

    private val _state = MutableStateFlow(TestEditorUiState())
    val state: StateFlow<TestEditorUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                val user = userId?.let { userRepository.getUser(it) }
                val clubId = user?.clubId

                // Проверка прав: director (owner / с content.edit) или instructor.
                val canEdit = user != null && (
                    PermissionChecker.hasPerm(user, "content.edit")
                    || user.role == "instructor"
                )

                if (!canEdit) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            canEdit = false,
                            errorMessage = "Нет доступа к редактору тестов",
                        )
                    }
                    return@launch
                }

                val isNew = testIdArg == "new"

                if (isNew) {
                    val initialScope = prefilledScope ?: "standalone"
                    val initialScopeId = prefilledScopeId
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isNew = true,
                            canEdit = true,
                            testId = UUID.randomUUID().toString(),
                            clubId = clubId,
                            scope = initialScope,
                            scopeId = initialScopeId,
                        )
                    }
                    if (initialScope != "standalone") {
                        loadScopeIdOptions(initialScope, clubId)
                    }
                } else {
                    val test = testRepository.getTest(testIdArg)
                    if (test == null) {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = "Тест не найден")
                        }
                        return@launch
                    }
                    val questions = testRepository.getQuestionsForTest(testIdArg)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isNew = false,
                            canEdit = true,
                            testId = test.id,
                            clubId = test.clubId,
                            title = test.title,
                            description = test.description,
                            scope = test.scope,
                            scopeId = test.scopeId,
                            passScoreText = test.passScore.toString(),
                            questionCount = questions.size,
                        )
                    }
                    loadScopeIdOptions(test.scope, clubId)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onPassScoreChange(v: String) {
        // разрешить только цифры
        val filtered = v.filter { it.isDigit() }.take(3)
        _state.update { it.copy(passScoreText = filtered) }
    }

    fun onScopeChange(newScope: String) {
        _state.update { it.copy(scope = newScope, scopeId = null, scopeIdOptions = emptyList()) }
        val clubId = _state.value.clubId ?: return
        loadScopeIdOptions(newScope, clubId)
    }

    fun onScopeIdChange(id: String) = _state.update { it.copy(scopeId = id) }

    private fun loadScopeIdOptions(scope: String, clubId: String?) {
        if (scope == "standalone" || clubId == null) return
        viewModelScope.launch {
            try {
                val options: List<Pair<String, String>> = when (scope) {
                    "honor" -> honorRepository.observeAllHonors().first()
                        .map { it.id to it.name }

                    "level" -> levelRepository.observeLevels().first()
                        .map { it.id to "Ступень ${it.order}: ${it.name}" }

                    "task" -> taskRepository.observeClubTasks(clubId).first()
                        .map { it.id to (it.title.ifBlank { it.id }) }

                    "homework" -> achievementRepository.observeActiveHomeworkByClub(clubId).first()
                        .map { it.id to (it.title.ifBlank { it.id }) }

                    "quest" -> qrQuestRepository.observeAllQuests(clubId).first()
                        .map { it.id to it.title }

                    "event" -> {
                        val now = System.currentTimeMillis()
                        val yearMs = 365L * 24 * 3600 * 1000
                        eventRepository.observeActiveInRange(clubId, now - yearMs, now + yearMs).first()
                            .map { it.id to it.title }
                    }

                    "book" -> bookRepository.observeAvailable(clubId).first()
                        .map { it.id to it.title }

                    else -> emptyList()
                }
                _state.update { it.copy(scopeIdOptions = options) }
            } catch (e: Exception) {
                _state.update { it.copy(scopeIdOptions = emptyList()) }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canEdit) {
            _state.update { it.copy(errorMessage = "Нет доступа") }
            return
        }
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        val passScore = s.passScoreText.toIntOrNull() ?: 90
        if (passScore !in 0..100) {
            _state.update { it.copy(errorMessage = "Проходной балл должен быть 0–100") }
            return
        }
        if (s.scope != "standalone" && s.scopeId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Выберите, к чему привязан тест") }
            return
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val entity = TestEntity(
                    id = s.testId,
                    clubId = s.clubId,
                    scope = s.scope,
                    scopeId = if (s.scope == "standalone") null else s.scopeId,
                    title = s.title.trim(),
                    description = s.description.trim(),
                    passScore = passScore,
                    archivedAt = null,
                    purgeAfter = null,
                    pendingSync = true,
                )
                testRepository.upsertTest(entity)
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка сохранения")
                }
            }
        }
    }

    fun showDeleteDialog() = _state.update { it.copy(showDeleteDialog = true) }
    fun hideDeleteDialog() = _state.update { it.copy(showDeleteDialog = false) }

    fun confirmDelete() {
        val id = _state.value.testId
        _state.update { it.copy(showDeleteDialog = false) }
        viewModelScope.launch {
            try {
                testRepository.deleteTest(id)
                _state.update { it.copy(isSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка удаления") }
            }
        }
    }
}
