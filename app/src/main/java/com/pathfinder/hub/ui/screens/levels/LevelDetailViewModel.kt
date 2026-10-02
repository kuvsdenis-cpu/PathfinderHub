package com.pathfinder.hub.ui.screens.levels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.ui.storage.FileUploadResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class LevelDetailUiState(
    val isLoading: Boolean = true,
    val level: LevelEntity? = null,
    val sections: List<SectionUiModel> = emptyList(),
    val approvedCount: Int = 0,
    val totalCount: Int = 0,
    val errorMessage: String? = null
)

data class SectionUiModel(
    val id: String,
    val name: String,
    val order: Int,
    val requirements: List<RequirementUiModel>
)

data class RequirementUiModel(
    val id: String,
    val sectionId: String,
    val text: String,
    val type: String,
    val order: Int,
    val status: String
)

@HiltViewModel
class LevelDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val levelRepository: LevelRepository
) : ViewModel() {

    private val levelId: String = savedStateHandle.get<String>("levelId") ?: ""

    private val _state = MutableStateFlow(LevelDetailUiState())
    val state: StateFlow<LevelDetailUiState> = _state.asStateFlow()

    init {
        loadLevelData()
    }

    private fun loadLevelData() {
        Log.d(TAG, "Получен levelId: '$levelId'")

        if (levelId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "ID ступени не указан") }
            return
        }

        val userId = sessionManager.getUserId() ?: "demo_user"

        viewModelScope.launch {
            try {
                runDiagnostics(levelId)

                val level = levelRepository.getLevel(levelId)
                val directSections = levelRepository.getSections(levelId)
                val directRequirements = levelRepository.getRequirements(levelId)

                if (directSections.isEmpty() && directRequirements.isEmpty()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            level = level,
                            sections = emptyList(),
                            totalCount = 0,
                            approvedCount = 0
                        )
                    }
                }

                val gotFlowEmission = MutableStateFlow(false)

                val flowJob = launch {
                    combine(
                        levelRepository.observeSections(levelId),
                        levelRepository.observeRequirements(levelId),
                        levelRepository.observeProgress(userId, levelId)
                    ) { sections, requirements, progressList ->
                        Triple(sections, requirements, progressList)
                    }
                        .catch { e -> Log.e(TAG, "Flow error", e) }
                        .collect { (sections, requirements, progressList) ->
                            gotFlowEmission.value = true
                            val newState = buildUiState(level, sections, requirements, progressList)
                            _state.update { newState }
                        }
                }

                withTimeoutOrNull(3000L) {
                    while (!gotFlowEmission.value) {
                        delay(100)
                    }
                }

                if (!gotFlowEmission.value && directRequirements.isNotEmpty()) {
                    val progress = levelRepository.getProgressSnapshot(userId, levelId)
                    val fallbackState = buildUiState(level, directSections, directRequirements, progress)
                    _state.update { fallbackState }
                }

                flowJob
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки данных ступени", e)
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    /**
     * Вызывается, когда пользователь вернулся с FileUploadScreen.
     * Прикрепляет загруженный файл к требованию.
     */
    fun attachUploadedFile(result: FileUploadResult) {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                levelRepository.attachReportToRequirement(
                    userId = userId,
                    requirementId = result.contextId,
                    fileUrl = result.fileUrl,
                    fileName = result.fileName
                )
                Log.d(TAG, "Файл прикреплён к требованию ${result.contextId}")
                // UI обновится автоматически через observeProgress в combine
            } catch (e: Exception) {
                Log.e(TAG, "attachUploadedFile failed", e)
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка прикрепления файла") }
            }
        }
    }

    private fun buildUiState(
        level: LevelEntity?,
        sections: List<com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity>,
        requirements: List<com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity>,
        progressList: List<com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity>
    ): LevelDetailUiState {
        val progressMap = progressList.associateBy { it.requirementId }
        var approvedCount = 0

        val uiSections = sections.map { section ->
            val reqs = requirements.filter { it.sectionId == section.id }.map { req ->
                val progress = progressMap[req.id]
                val status = progress?.status ?: "not_started"
                if (status == "approved") approvedCount++

                RequirementUiModel(
                    id = req.id,
                    sectionId = req.sectionId,
                    text = req.text,
                    type = req.type,
                    order = req.order,
                    status = status
                )
            }.sortedBy { it.order }

            SectionUiModel(
                id = section.id,
                name = section.name,
                order = section.order,
                requirements = reqs
            )
        }.sortedBy { it.order }

        return LevelDetailUiState(
            isLoading = false,
            level = level,
            sections = uiSections,
            approvedCount = approvedCount,
            totalCount = requirements.size
        )
    }

    private suspend fun runDiagnostics(levelId: String) {
        try {
            val distinctReqLevelIds = levelRepository.debugDistinctRequirementLevelIds()
            Log.d(TAG, "DISTINCT levelId в level_requirements: $distinctReqLevelIds")

            val distinctSecLevelIds = levelRepository.debugDistinctSectionLevelIds()
            Log.d(TAG, "DISTINCT levelId в level_sections: $distinctSecLevelIds")

            val matchInSections = distinctSecLevelIds.contains(levelId)
            val matchInReqs = distinctReqLevelIds.contains(levelId)
            Log.d(TAG, "Совпадение levelId='$levelId': sections=$matchInSections, requirements=$matchInReqs")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка диагностики", e)
        }
    }

    fun onRequirementAction(requirementId: String, type: String) {
        Log.d(TAG, "onRequirementAction: requirementId=$requirementId, type=$type")
    }

    companion object {
        private const val TAG = "LevelDetailVM"
    }
}