package com.pathfinder.hub.ui.learning.editor

import android.content.Context
import android.net.Uri
import com.pathfinder.hub.data.repository.StorageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import com.pathfinder.hub.data.repository.RequirementContentRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequirementEditorUiState(
    val isLoading: Boolean = true,
    val materials: List<RequirementMaterialEntity> = emptyList(),
    val tasks: List<RequirementTaskEntity> = emptyList(),
    val userRole: String = "",
    val canEdit: Boolean = false,
    val canFlag: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val uploadedMaterialUrl: String? = null,
    val isUploading: Boolean = false,
)

@HiltViewModel
class RequirementEditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val contentRepo: RequirementContentRepository,
    private val storage: StorageRepository,
) : ViewModel() {

    private val requirementId: String = savedStateHandle.get<String>("requirementId") ?: ""

    private val _state = MutableStateFlow(RequirementEditorUiState())
    val state: StateFlow<RequirementEditorUiState> = _state.asStateFlow()

    private var clubId: String? = null
    private var userId: String? = null

    init { load() }

    private fun load() {
        val uid = sessionManager.getUserId()
        if (uid == null || requirementId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет данных") }
            return
        }
        userId = uid

        viewModelScope.launch {
            val user = userRepository.getUser(uid)
            val cid = user?.clubId
            if (cid.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            clubId = cid
            val role = user.role
            // #14: как в ContentManagerViewModel и TestEditorViewModel —
            // director (owner / co-director с content.edit) или instructor.
            val canEdit = PermissionChecker.hasPerm(user, "content.edit")
                    || user.isOwnerClub
            // Флаг «надо поправить» — только для тех, кто не может редактировать.
            val canFlag = !canEdit && role in listOf("instructor", "secretary")

            _state.update { it.copy(userRole = role, canEdit = canEdit, canFlag = canFlag) }

            launch {
                contentRepo.observeMaterials(cid, requirementId).collect { list ->
                    _state.update { it.copy(materials = list.sortedBy { m -> m.orderIndex }, isLoading = false) }
                }
            }
            launch {
                contentRepo.observeTasks(cid, requirementId).collect { list ->
                    _state.update { it.copy(tasks = list.sortedBy { t -> t.orderIndex }) }
                }
            }
        }
    }

    // ==================== CRUD: MATERIALS ====================

    fun saveMaterial(
        existing: RequirementMaterialEntity?,
        type: String, title: String, content: String,
        orderIndex: Int,
    ) {
        val cid = clubId ?: return
        val uid = userId ?: return
        if (!_state.value.canEdit) return

        viewModelScope.launch {
            val r = if (existing == null) {
                contentRepo.createMaterial(cid, requirementId, uid, type, title, content, orderIndex)
            } else {
                contentRepo.saveMaterial(
                    existing.copy(type = type, title = title, content = content, orderIndex = orderIndex)
                )
            }
            r.fold(
                onSuccess = { _state.update { it.copy(infoMessage = "Сохранено") } },
                onFailure = { e -> _state.update { it.copy(errorMessage = e.message) } }
            )
        }
    }

    fun deleteMaterial(m: RequirementMaterialEntity) {
        val cid = clubId ?: return
        if (!_state.value.canEdit) return
        viewModelScope.launch {
            contentRepo.deleteMaterial(cid, requirementId, m.id)
        }
    }

    // ==================== CRUD: TASKS ====================

    fun saveTask(
        existing: RequirementTaskEntity?,
        type: String, title: String, description: String?,
        options: List<String>, correctAnswer: String?,
        isRequired: Boolean, orderIndex: Int,
    ) {
        val cid = clubId ?: return
        val uid = userId ?: return
        if (!_state.value.canEdit) return

        val correctSerialized: String? = when {
            type == "fill_blank" && !correctAnswer.isNullOrBlank() -> {
                try {
                    val list = correctAnswer.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    com.google.gson.Gson().toJson(list)
                } catch (_: Exception) { correctAnswer }
            }
            type == "match" -> {
                try {
                    val flat = options
                    val pairs = flat.chunked(2).mapIndexed { i, _ -> listOf(i * 2, i * 2 + 1) }
                    com.google.gson.Gson().toJson(pairs)
                } catch (_: Exception) { null }
            }
            else -> correctAnswer
        }

        viewModelScope.launch {
            val r = if (existing == null) {
                contentRepo.createTask(
                    cid, requirementId, uid, type, title, description,
                    options, correctSerialized, isRequired, orderIndex
                )
            } else {
                contentRepo.saveTask(
                    existing.copy(
                        type = type, title = title, description = description,
                        options = options, correctAnswer = correctSerialized,
                        isRequired = isRequired, orderIndex = orderIndex,
                        autoCheck = RequirementTaskEntity.isAutoCheckType(type)
                    )
                )
            }
            r.fold(
                onSuccess = { _state.update { it.copy(infoMessage = "Сохранено") } },
                onFailure = { e -> _state.update { it.copy(errorMessage = e.message) } }
            )
        }
    }

    fun deleteTask(t: RequirementTaskEntity) {
        val cid = clubId ?: return
        if (!_state.value.canEdit) return
        viewModelScope.launch { contentRepo.deleteTask(cid, requirementId, t.id) }
    }

    // ==================== FLAG (наставник) ====================

    fun flagMaterial(m: RequirementMaterialEntity, comment: String?) {
        val cid = clubId ?: return
        val uid = userId ?: return
        if (!_state.value.canFlag) return
        viewModelScope.launch {
            contentRepo.flagMaterial(cid, requirementId, m.id, uid, comment)
        }
    }

    fun unflagMaterial(m: RequirementMaterialEntity) {
        val cid = clubId ?: return
        if (!_state.value.canFlag) return
        viewModelScope.launch { contentRepo.unflagMaterial(cid, requirementId, m.id) }
    }

    fun flagTask(t: RequirementTaskEntity, comment: String?) {
        val cid = clubId ?: return
        val uid = userId ?: return
        if (!_state.value.canFlag) return
        viewModelScope.launch { contentRepo.flagTask(cid, requirementId, t.id, uid, comment) }
    }

    fun unflagTask(t: RequirementTaskEntity) {
        val cid = clubId ?: return
        if (!_state.value.canFlag) return
        viewModelScope.launch { contentRepo.unflagTask(cid, requirementId, t.id) }
    }

    // ==================== ЗАГРУЗКА ФАЙЛА МАТЕРИАЛА ====================

    fun uploadMaterialFile(uri: Uri, title: String) {
        if (!_state.value.canEdit) return
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, errorMessage = null) }
            try {
                val file = uriToFile(uri)
                if (file == null) {
                    _state.update { it.copy(isUploading = false, errorMessage = "Не удалось прочитать файл") }
                    return@launch
                }
                val ext = file.extension.ifBlank { "bin" }
                val key = storage.buildObjectKey("requirements/$requirementId/materials", title.ifBlank { "material" }, ext)
                val r = storage.uploadFile(file, key) { /* progress */ }
                r.fold(
                    onSuccess = { url ->
                        _state.update {
                            it.copy(
                                isUploading = false,
                                uploadedMaterialUrl = url,
                                infoMessage = "Файл загружен"
                            )
                        }
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(isUploading = false, errorMessage = e.message ?: "Ошибка загрузки")
                        }
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isUploading = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }

    fun clearUploadedUrl() {
        _state.update { it.copy(uploadedMaterialUrl = null) }
    }

    private suspend fun uriToFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val input = appContext.contentResolver.openInputStream(uri) ?: return@withContext null
            val file = File(appContext.cacheDir, "upload_${System.currentTimeMillis()}")
            file.outputStream().use { input.copyTo(it) }
            file
        } catch (e: Exception) {
            null
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }

    companion object { private const val TAG = "ReqEditorVM" }
}
