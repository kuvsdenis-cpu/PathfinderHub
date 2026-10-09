package com.pathfinder.hub.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.repository.QrStationRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class CreateStationUiState(
    val name: String = "",
    val description: String = "",
    val type: String = "entry",        // entry / quest / custom
    val rewardPoints: String = "10",
    val rewardAchievementId: String = "",
    val latitude: String = "55.7539",
    val longitude: String = "37.6208",
    val radiusMeters: String = "100",
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
    val createdId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CreateStationViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrRepository: QrStationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateStationUiState())
    val state: StateFlow<CreateStationUiState> = _state.asStateFlow()

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onType(v: String) = _state.update { it.copy(type = v) }
    fun onRewardPoints(v: String) = _state.update { it.copy(rewardPoints = v.filter { c -> c.isDigit() }) }
    fun onRewardAchievement(v: String) = _state.update { it.copy(rewardAchievementId = v) }
    fun onLatitude(v: String) = _state.update { it.copy(latitude = v) }
    fun onLongitude(v: String) = _state.update { it.copy(longitude = v) }
    fun onRadius(v: String) = _state.update { it.copy(radiusMeters = v.filter { c -> c.isDigit() }) }

    fun create() {
        val s = _state.value
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(errorMessage = "Не авторизован") }
            return
        }
        if (s.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        val lat = s.latitude.toDoubleOrNull()
        val lon = s.longitude.toDoubleOrNull()
        if (lat == null || lon == null) {
            _state.update { it.copy(errorMessage = "Неверные координаты") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                val id = "qr_" + UUID.randomUUID().toString().take(8)
                val now = Date()
                val entity = QrStationEntity(
                    id = id,
                    clubId = clubId,
                    eventId = null,
                    name = s.name.trim(),
                    description = s.description.trim(),
                    type = s.type,
                    rewardPoints = s.rewardPoints.toIntOrNull() ?: 0,
                    rewardAchievementId = s.rewardAchievementId.trim().ifBlank { null },
                    latitude = lat,
                    longitude = lon,
                    radiusMeters = s.radiusMeters.toIntOrNull() ?: 100,
                    status = "active",
                    createdBy = uid,
                    createdAt = now,
                    updatedAt = now,
                    pendingSync = true,
                )
                qrRepository.upsertStation(entity)
                _state.update { it.copy(isLoading = false, isCreated = true, createdId = id) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка")
                }
            }
        }
    }
}
