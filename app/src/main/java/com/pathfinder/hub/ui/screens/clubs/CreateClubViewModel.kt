package com.pathfinder.hub.ui.screens.clubs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.repository.ClubRepository
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

data class CreateClubUiState(
    val name: String = "",
    val city: String = "",
    val region: String = "",
    val address: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CreateClubViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateClubUiState())
    val state: StateFlow<CreateClubUiState> = _state.asStateFlow()

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onCity(v: String) = _state.update { it.copy(city = v) }
    fun onRegion(v: String) = _state.update { it.copy(region = v) }
    fun onAddress(v: String) = _state.update { it.copy(address = v) }
    fun onContactEmail(v: String) = _state.update { it.copy(contactEmail = v) }
    fun onContactPhone(v: String) = _state.update { it.copy(contactPhone = v) }

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

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val user = userRepository.getUser(uid)
                if (user == null) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
                    return@launch
                }

                val clubId = "club_" + UUID.randomUUID().toString().take(8)
                val now = Date()

                val club = ClubEntity(
                    id = clubId,
                    conferenceId = user.conferenceId ?: "demo_conf",
                    name = s.name.trim(),
                    logoUrl = null,
                    city = s.city.trim(),
                    region = s.region.trim(),
                    contactEmail = s.contactEmail.trim(),
                    contactPhone = s.contactPhone.trim(),
                    foundedAt = null,
                    motto = null,
                    meetingDay = "saturday",
                    startTime = "10:00",
                    endTime = "13:00",
                    location = s.address.trim(),
                    frequency = "weekly",
                    autoAcceptInvite = true,
                    inviteLifetimeDays = 7,
                    requireParentalConsent = true,
                    maxMembers = null,
                    paymentEnabled = false,
                    membershipFee = null,
                    membershipPeriod = null,
                    paymentMethods = emptyList(),
                    paymentRemindersEnabled = false,
                    autoModerationEnabled = true,
                    reviewWindowHours = 6,
                    escalationHours = 12,
                    defaultReportTemplateId = null,
                    reportPeriods = listOf("month", "quarter", "year"),
                    autoGenerateReports = false,
                    updatedAt = now,
                    updatedBy = uid,
                )

                clubRepository.upsert(club)
                userRepository.addClubToUser(uid, clubId)
                userRepository.setActiveClub(uid, clubId)

                _state.update { it.copy(isLoading = false, isCreated = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка создания")
                }
            }
        }
    }
}
