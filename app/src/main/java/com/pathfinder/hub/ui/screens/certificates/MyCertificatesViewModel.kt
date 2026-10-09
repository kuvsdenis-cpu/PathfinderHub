package com.pathfinder.hub.ui.screens.certificates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.CertificateEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.StorageRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

data class CertificateItem(
    val certificateId: String,
    val title: String,
    val name: String,
    val issuedAt: Date,
    val kind: String,
    val targetId: String,
)

data class MyCertificatesUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val items: List<CertificateItem> = emptyList(),
    val uploaded: Map<String, CertificateEntity> = emptyMap(),
    val uploadingId: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class MyCertificatesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val achievementRepository: AchievementRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyCertificatesUiState())
    val state: StateFlow<MyCertificatesUiState> = _state.asStateFlow()

    private val userId: String? = sessionManager.getUserId()

    init {
        val uid = userId
        if (uid == null) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "Пользователь не авторизован")
            }
        } else {
            load(uid)
        }
    }

    private fun load(userId: String) {
        // Имя
        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            val name = listOfNotNull(user?.firstName, user?.lastName)
                .joinToString(" ").trim().ifBlank { "Следопыт" }
            _state.update { it.copy(userName = name) }
        }

        // Завершённые ступени
        viewModelScope.launch {
            try {
                levelRepository.observeCompletions(userId).collect { levels ->
                    val items = levels.map { lc ->
                        val lvl = levelRepository.getLevel(lc.levelId)
                        CertificateItem(
                            certificateId = "level_" + lc.levelId,
                            title = "Сертификат о прохождении ступени",
                            name = lvl?.name ?: lc.levelId,
                            issuedAt = lc.completedAt,
                            kind = "level",
                            targetId = lc.levelId,
                        )
                    }
                    _state.update { s ->
                        s.copy(
                            items = (s.items.filter { it.kind != "level" } + items)
                                .sortedByDescending { it.issuedAt },
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }

        // Завершённые специализации
        viewModelScope.launch {
            try {
                honorRepository.observeCompletions(userId).collect { honors ->
                    val items = honors.map { hc ->
                        val h = honorRepository.getHonor(hc.honorId)
                        CertificateItem(
                            certificateId = "honor_" + hc.honorId,
                            title = "Сертификат о специализации",
                            name = h?.name ?: hc.honorId,
                            issuedAt = hc.completedAt,
                            kind = "honor",
                            targetId = hc.honorId,
                        )
                    }
                    _state.update { s ->
                        s.copy(
                            items = (s.items.filter { it.kind != "honor" } + items)
                                .sortedByDescending { it.issuedAt },
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }

        // Уже загруженные в облако
        viewModelScope.launch {
            try {
                achievementRepository.observeCertificates(userId).collect { list ->
                    _state.update { s ->
                        s.copy(uploaded = list.associateBy { it.id })
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Генерирует PDF локально, грузит в Yandex Bucket, сохраняет URL.
     */
    fun uploadCertificate(item: CertificateItem) {
        val uid = userId ?: return
        _state.update { it.copy(uploadingId = item.certificateId, message = null) }

        viewModelScope.launch {
            try {
                // 1. Генерация PDF (IO)
                val file = withContext(Dispatchers.IO) {
                    CertificatePdfGenerator.generate(
                        context = context,
                        certificateId = item.certificateId,
                        userName = _state.value.userName,
                        title = item.title,
                        subject = item.name,
                        issuedAt = item.issuedAt,
                    )
                }

                // 2. Upload в Yandex
                val safeId = item.certificateId.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val objectKey = "certificates/$uid/${safeId}.pdf"

                val uploadResult = storageRepository.uploadFile(file, objectKey) { }

                uploadResult.fold(
                    onSuccess = { url ->
                        // 3. Сохранить локально + Firestore
                        val entity = CertificateEntity(
                            id = item.certificateId,
                            userId = uid,
                            type = item.kind,
                            targetId = item.targetId,
                            issuedAt = item.issuedAt,
                            pdfUrl = url,
                            imageUrl = "",
                        )
                        achievementRepository.upsertCertificateWithSync(entity)
                        file.delete()

                        _state.update {
                            it.copy(uploadingId = null, message = "Сертификат в облаке")
                        }
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(
                                uploadingId = null,
                                errorMessage = "Ошибка загрузки: " + (e.message ?: "?")
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        uploadingId = null,
                        errorMessage = e.message ?: "Ошибка обработки"
                    )
                }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(message = null, errorMessage = null) }
}
