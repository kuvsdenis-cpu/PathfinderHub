#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Восстановление CreateClubScreen + CreateClubViewModel."""
import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        print("[SKIP] " + path + " (уже есть)")
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


# ============================================================
# 1. CreateClubViewModel.kt
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.clubs

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
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/clubs/CreateClubViewModel.kt", VM)


# ============================================================
# 2. CreateClubScreen.kt
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.screens.clubs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClubScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateClubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый клуб") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Основная информация", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text("Название клуба *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.city,
                onValueChange = viewModel::onCity,
                label = { Text("Город") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.region,
                onValueChange = viewModel::onRegion,
                label = { Text("Регион") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddress,
                label = { Text("Адрес встреч") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.contactEmail,
                onValueChange = viewModel::onContactEmail,
                label = { Text("Контактный email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.contactPhone,
                onValueChange = viewModel::onContactPhone,
                label = { Text("Контактный телефон") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        msg,
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::create,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Создать клуб")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "После создания вы станете директором этого клуба. " +
                        "Он появится во вкладках сверху.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/clubs/CreateClubScreen.kt", SCREEN)

print()
print("=" * 60)
print("Готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")