package com.pathfinder.hub.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.repository.InviteRepository
import com.pathfinder.hub.domain.usecase.auth.RegisterByInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class RegisterUiState(
    val inviteCode: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val birthDate: String = "2010-01-01",
    val clubId: String? = null,
    val role: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registerSuccess: Boolean = false,
    val userId: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterByInviteUseCase,
    private val inviteRepository: InviteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onInviteCodeChange(value: String) {
        _state.update { it.copy(inviteCode = value.uppercase(), errorMessage = null) }
    }

    fun onFirstNameChange(value: String) {
        _state.update { it.copy(firstName = value) }
    }

    fun onLastNameChange(value: String) {
        _state.update { it.copy(lastName = value) }
    }

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value) }
    }

    fun onBirthDateChange(value: String) {
        _state.update { it.copy(birthDate = value) }
    }

    /** Проверка инвайт-кода перед регистрацией. */
    fun validateInvite() {
        val code = _state.value.inviteCode.trim()
        if (code.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите код") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val invite = inviteRepository.getByCode(code)
            when {
                invite == null -> _state.update {
                    it.copy(isLoading = false, errorMessage = "Код не найден")
                }
                invite.status != "active" -> _state.update {
                    it.copy(isLoading = false, errorMessage = "Код уже использован или просрочен")
                }
                invite.expiresAt.before(Date()) -> _state.update {
                    it.copy(isLoading = false, errorMessage = "Срок действия кода истёк")
                }
                else -> _state.update {
                    it.copy(
                        isLoading = false,
                        clubId = invite.clubId,
                        role = invite.role,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun register() {
        val s = _state.value
        if (s.clubId == null || s.role == null) {
            _state.update { it.copy(errorMessage = "Сначала проверьте инвайт-код") }
            return
        }
        if (s.firstName.isBlank() || s.lastName.isBlank() || s.email.isBlank()) {
            _state.update { it.copy(errorMessage = "Заполните все поля") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val birthDate = try {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .parse(s.birthDate) ?: Date()
            } catch (e: Exception) { Date() }

            val result = registerUseCase(
                inviteCode = s.inviteCode.trim(),
                firstName = s.firstName.trim(),
                lastName = s.lastName.trim(),
                email = s.email.trim(),
                birthDate = birthDate
            )
            result.fold(
                onSuccess = { userId ->
                    _state.update {
                        it.copy(isLoading = false, registerSuccess = true, userId = userId)
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Ошибка регистрации"
                        )
                    }
                }
            )
        }
    }
}