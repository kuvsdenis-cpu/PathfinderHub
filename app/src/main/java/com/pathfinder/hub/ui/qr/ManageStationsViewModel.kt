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
import javax.inject.Inject

data class ManageStationsUiState(
    val isLoading: Boolean = true,
    val stations: List<QrStationEntity> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ManageStationsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrRepository: QrStationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageStationsUiState())
    val state: StateFlow<ManageStationsUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            viewModelScope.launch {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                qrRepository.observeAllStations(clubId).collect { list ->
                    _state.update { it.copy(isLoading = false, stations = list) }
                }
            }
        }
    }

    fun deleteStation(id: String) {
        viewModelScope.launch {
            try { qrRepository.deleteStation(id) }
            catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }
}
