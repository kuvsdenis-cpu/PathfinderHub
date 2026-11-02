package com.pathfinder.hub.ui.screens.home.parent

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.parent.EventConsentEntity
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.StorageRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import javax.inject.Inject

data class ConsentItem(
    val consent: EventConsentEntity,
    val event: EventEntity?,
    val child: UserEntity?,
)

data class ParentConsentsUiState(
    val isLoading: Boolean = true,
    val items: List<ConsentItem> = emptyList(),
    val uploadingKey: String? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ParentConsentsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val parentRepository: ParentRepository,
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentConsentsUiState())
    val state: StateFlow<ParentConsentsUiState> = _state.asStateFlow()

    private val parentId: String? = sessionManager.getUserId()

    init {
        val uid = parentId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(parentId: String) {
        viewModelScope.launch {
            parentRepository.observeConsentsByParent(parentId).collect { list ->
                // Refresh presigned documentUrl (TTL 7 дней), только если истекает
                list.forEach { c ->
                    val url = c.documentUrl ?: return@forEach
                    try {
                        val fresh = storageRepository.refreshPresignedUrl(url, ifFreshHours = 24)
                        if (fresh != null) {
                            parentRepository.upsertConsent(c.copy(documentUrl = fresh))
                        }
                    } catch (_: Exception) {}
                }
                val items = list.map { c ->
                    ConsentItem(
                        consent = c,
                        event = try { eventRepository.getEvent(c.eventId) } catch (_: Exception) { null },
                        child = userRepository.getUser(c.childId),
                    )
                }
                _state.update { it.copy(isLoading = false, items = items) }
            }
        }
    }

    private fun key(item: ConsentItem) =
        "${item.consent.eventId}_${item.consent.childId}"

    fun respond(item: ConsentItem, approve: Boolean) {
        val uid = parentId ?: return

        // Запрет approve без подписанного документа
        if (approve && item.consent.documentUrl.isNullOrBlank()) {
            _state.update {
                it.copy(errorMessage = "Сначала приложите фото подписанного согласия")
            }
            return
        }

        viewModelScope.launch {
            try {
                parentRepository.upsertConsent(
                    item.consent.copy(
                        status = if (approve) "approved" else "declined",
                        respondedAt = Date(),
                    )
                )
                _state.update {
                    it.copy(infoMessage = if (approve) "Согласие дано" else "Отказ сохранён")
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /**
     * Родитель выбрал фото → копируем в temp → загружаем в Yandex →
     * сохраняем URL в consent.documentUrl.
     */
    fun uploadDocument(item: ConsentItem, uri: Uri) {
        val uid = parentId ?: return
        val k = key(item)
        _state.update { it.copy(uploadingKey = k, errorMessage = null) }

        viewModelScope.launch {
            try {
                val tempFile = copyUriToTempFile(uri)
                val childName = listOfNotNull(item.child?.firstName, item.child?.lastName)
                    .filter { it.isNotBlank() }.joinToString("_").ifBlank { item.consent.childId }
                val eventName = item.event?.title?.ifBlank { null } ?: item.consent.eventId
                val objectKey = storageRepository.buildObjectKey(
                    "event_consents/$uid", "${eventName}_$childName", "jpg")

                val result = storageRepository.uploadFile(tempFile, objectKey) { }
                tempFile.delete()

                result.fold(
                    onSuccess = { url ->
                        parentRepository.upsertConsent(
                            item.consent.copy(documentUrl = url)
                        )
                        _state.update {
                            it.copy(
                                uploadingKey = null,
                                infoMessage = "Фото приложено",
                            )
                        }
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(
                                uploadingKey = null,
                                errorMessage = "Ошибка загрузки: " + (e.message ?: "?")
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("ParentConsents", "uploadDocument failed", e)
                _state.update {
                    it.copy(uploadingKey = null, errorMessage = e.message ?: "Ошибка")
                }
            }
        }
    }

    private fun copyUriToTempFile(uri: Uri): File {
        val f = File(context.cacheDir, "consent_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(f).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Не удалось открыть файл")
        return f
    }

    fun clearMessages() = _state.update { it.copy(infoMessage = null, errorMessage = null) }
}
