#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Fix payments + NavHost routes - 09.10.2026
Запуск:
    python fix_payments_final.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def rewrite(path, content):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[REWRITE] " + path)


def patch(path, repls, skip_if_marker=None):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
    if skip_if_marker and skip_if_marker in text:
        print("[SKIP] " + path + " (уже пропатчен)")
        return
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    for i, (o, n) in enumerate(repls, 1):
        if o not in text:
            die(path + ": шаблон #" + str(i) + ":\n" + o[:400])
        text = text.replace(o, n, 1)
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. DirectorPaymentsViewModel — фикс emptyList()
# ============================================================
DP_VM = '''package com.pathfinder.hub.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DirectorPaymentsUiState(
    val isLoading: Boolean = true,
    val all: List<PaymentEntity> = emptyList(),
    val filtered: List<PaymentEntity> = emptyList(),
    val selectedFilter: String = "waiting",
    val namesByParent: Map<String, String> = emptyMap(),
    val processingId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class DirectorPaymentsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorPaymentsUiState())
    val state: StateFlow<DirectorPaymentsUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }
        viewModelScope.launch {
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            userRepository.observeClubMembersByRole(clubId, "parent").collect { parents ->
                val names = parents.associate {
                    it.id to "${it.firstName} ${it.lastName}".trim()
                }
                _state.update { it.copy(namesByParent = names, isLoading = false) }

                parents.forEach { p ->
                    launch {
                        parentRepository.observeParentPayments(p.id).collect { list ->
                            _state.update { s ->
                                val others = s.all.filter { it.parentId != p.id }
                                val merged = (others + list).sortedByDescending { it.dueDate }
                                s.copy(all = merged, filtered = applyFilter(merged, s.selectedFilter))
                            }
                        }
                    }
                }
            }
        }
    }

    fun setFilter(filter: String) {
        _state.update {
            it.copy(selectedFilter = filter, filtered = applyFilter(it.all, filter))
        }
    }

    private fun applyFilter(list: List<PaymentEntity>, filter: String): List<PaymentEntity> {
        return when (filter) {
            "waiting" -> list.filter {
                it.status == "pending" || it.status == "paid" || it.status == "overdue"
            }
            "paid" -> list.filter { it.status == "paid" }
            "confirmed" -> list.filter { it.status == "confirmed" }
            else -> list
        }
    }

    fun confirm(p: PaymentEntity) {
        val uid = sessionManager.getUserId() ?: return
        _state.update { it.copy(processingId = p.id) }
        viewModelScope.launch {
            try {
                parentRepository.confirmPayment(p.id, "confirmed", uid, System.currentTimeMillis())
                _state.update {
                    it.copy(processingId = null, infoMessage = "Оплата подтверждена")
                }
            } catch (e: Exception) {
                _state.update { it.copy(processingId = null, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/payments/DirectorPaymentsViewModel.kt", DP_VM)


# ============================================================
# 2. CreatePaymentViewModel — фикс first()
# ============================================================
CP_VM = '''package com.pathfinder.hub.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class TeenOption(
    val teen: UserEntity,
    val parents: List<UserEntity>,
)

data class CreatePaymentUiState(
    val isLoading: Boolean = true,
    val clubId: String? = null,
    val teens: List<TeenOption> = emptyList(),
    val selectedTeenId: String? = null,
    val selectedParentId: String? = null,
    val title: String = "",
    val amount: String = "",
    val currency: String = "RUB",
    val dueDate: Date? = null,
    val type: String = "membership",
    val immediatelyPaid: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CreatePaymentViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreatePaymentUiState())
    val state: StateFlow<CreatePaymentUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }
        viewModelScope.launch {
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            _state.update { it.copy(clubId = clubId) }

            userRepository.observeClubMembersByRole(clubId, "teen").collect { teens ->
                val options = mutableListOf<TeenOption>()
                for (t in teens) {
                    val parents = try {
                        parentRepository.observeParents(t.id).first()
                    } catch (_: Exception) { emptyList() }
                    val parentUsers = parents
                        .filter { it.status == "active" }
                        .mapNotNull { userRepository.getUser(it.parentId) }
                    options.add(TeenOption(teen = t, parents = parentUsers))
                }
                _state.update { it.copy(isLoading = false, teens = options) }
            }
        }
    }

    fun onTeenSelected(teenId: String) {
        val opt = _state.value.teens.find { it.teen.id == teenId }
        val autoParent = opt?.parents?.firstOrNull()?.id
        _state.update {
            it.copy(
                selectedTeenId = teenId,
                selectedParentId = if (opt?.parents?.size == 1) autoParent else it.selectedParentId,
            )
        }
    }

    fun onParentSelected(parentId: String) = _state.update { it.copy(selectedParentId = parentId) }
    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onAmount(v: String) = _state.update {
        it.copy(amount = v.filter { c -> c.isDigit() || c == '.' || c == ',' })
    }
    fun onCurrency(v: String) = _state.update { it.copy(currency = v) }
    fun onDueDate(v: Date) = _state.update { it.copy(dueDate = v) }
    fun onType(v: String) = _state.update { it.copy(type = v) }
    fun onImmediatelyPaid(v: Boolean) = _state.update { it.copy(immediatelyPaid = v) }

    fun save() {
        val s = _state.value
        val uid = sessionManager.getUserId() ?: return
        val clubId = s.clubId ?: return

        if (s.selectedTeenId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Выберите ребёнка") }
            return
        }
        if (s.selectedParentId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Выберите родителя") }
            return
        }
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите назначение") }
            return
        }
        val amount = s.amount.replace(',', '.').toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _state.update { it.copy(errorMessage = "Введите корректную сумму") }
            return
        }
        if (s.dueDate == null) {
            _state.update { it.copy(errorMessage = "Укажите срок") }
            return
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val now = Date()
                val status = if (s.immediatelyPaid) "confirmed" else "pending"
                val entity = PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    clubId = clubId,
                    childId = s.selectedTeenId,
                    parentId = s.selectedParentId,
                    type = s.type,
                    title = s.title.trim(),
                    amount = amount,
                    currency = s.currency.trim().ifBlank { "RUB" },
                    dueDate = s.dueDate,
                    status = status,
                    paymentMethod = if (s.immediatelyPaid) "manual" else null,
                    paidAt = if (s.immediatelyPaid) now else null,
                    confirmedBy = if (s.immediatelyPaid) uid else null,
                    confirmedAt = if (s.immediatelyPaid) now else null,
                    receiptUrl = null,
                    createdBy = uid,
                    createdAt = now,
                )
                parentRepository.upsertPayment(entity)
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/payments/CreatePaymentViewModel.kt", CP_VM)


# ============================================================
# 3. DirectorHomeScreen — добавить "Взносы"
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeScreen.kt", [
    ("""    onNavigateToQrQuests: () -> Unit = {},
    onNavigateToHomework: () -> Unit = {},
    onNavigateToReviewHomework: () -> Unit = {},""",
     """    onNavigateToQrQuests: () -> Unit = {},
    onNavigateToHomework: () -> Unit = {},
    onNavigateToReviewHomework: () -> Unit = {},
    onNavigateToPayments: () -> Unit = {},"""),

    ("""                // ---------- Ряд 8: QR-квесты + пусто ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        icon = Icons.Default.Route,
                        title = "QR-квесты",
                        description = "Серии станций",
                        perm = "qr.create",
                        user = u,
                        onClick = { guard("qr.create") { onNavigateToQrQuests() } },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.weight(1f))
                }""",
     """                // ---------- Ряд 8: QR-квесты + Взносы ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        icon = Icons.Default.Route,
                        title = "QR-квесты",
                        description = "Серии станций",
                        perm = "qr.create",
                        user = u,
                        onClick = { guard("qr.create") { onNavigateToQrQuests() } },
                        modifier = Modifier.weight(1f),
                    )
                    GuardedCard(
                        icon = Icons.Default.AccountBalance,
                        title = "Взносы",
                        description = "Оплата от родителей",
                        perm = "finance.view",
                        user = u,
                        onClick = { guard("finance.view") { onNavigateToPayments() } },
                        modifier = Modifier.weight(1f),
                    )
                }"""),
], skip_if_marker="onNavigateToPayments")


# ============================================================
# 4. PathfinderNavHost — импорты + DIRECTOR_HOME + новые экраны
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    # --- 4a. Импорты ---
    ("import com.pathfinder.hub.ui.screens.home.parent.ParentMessagesScreen",
     "import com.pathfinder.hub.ui.screens.home.parent.ParentMessagesScreen\n"
     "import com.pathfinder.hub.ui.screens.payments.DirectorPaymentsScreen\n"
     "import com.pathfinder.hub.ui.screens.payments.CreatePaymentScreen\n"
     "import com.pathfinder.hub.ui.screens.home.director.staff.StaffScreen\n"
     "import com.pathfinder.hub.ui.screens.home.director.staff.ManagePermissionsScreen\n"
     "import com.pathfinder.hub.ui.qr.quests.ManageQuestsScreen\n"
     "import com.pathfinder.hub.ui.qr.quests.QuestEditorScreen\n"
     "import com.pathfinder.hub.ui.screens.homework.CreateHomeworkScreen\n"
     "import com.pathfinder.hub.ui.screens.homework.review.ReviewHomeworkScreen\n"
     "import com.pathfinder.hub.ui.screens.clubs.CreateClubScreen"),

    # --- 4b. composable(DIRECTOR_HOME) — полный блок ---
    ("""        composable(Routes.DIRECTOR_HOME) {
            DirectorHomeScreen(
                onNavigateToInvites = { navController.navigate(Routes.DIRECTOR_INVITES) },
                onNavigateToMembers = { navController.navigate(Routes.CLUB_MEMBERS) },
                onNavigateToApprovals = { navController.navigate(Routes.APPROVALS) },
                onNavigateToReports = { navController.navigate(Routes.DIRECTOR_REPORTS) },
                onNavigateToEvents = { navController.navigate(Routes.CLUB_EVENTS) },
                onNavigateToSettings = { navController.navigate(Routes.CLUB_SETTINGS) },
                onNavigateToTasks = { navController.navigate(Routes.CREATE_TASK) },
                onNavigateToClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onNavigateToModeration = { navController.navigate(Routes.MODERATION) },
                onNavigateToContentManager = { navController.navigate(Routes.CONTENT_MANAGER) },
                onNavigateToQrStations = { navController.navigate(Routes.MY_STATIONS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }""",
     """        composable(Routes.DIRECTOR_HOME) {
            DirectorHomeScreen(
                onNavigateToInvites = { navController.navigate(Routes.DIRECTOR_INVITES) },
                onNavigateToMembers = { navController.navigate(Routes.CLUB_MEMBERS) },
                onNavigateToApprovals = { navController.navigate(Routes.APPROVALS) },
                onNavigateToReports = { navController.navigate(Routes.DIRECTOR_REPORTS) },
                onNavigateToEvents = { navController.navigate(Routes.CLUB_EVENTS) },
                onNavigateToSettings = { navController.navigate(Routes.CLUB_SETTINGS) },
                onNavigateToTasks = { navController.navigate(Routes.CREATE_TASK) },
                onNavigateToClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onNavigateToModeration = { navController.navigate(Routes.MODERATION) },
                onNavigateToContentManager = { navController.navigate(Routes.CONTENT_MANAGER) },
                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToQrStations = { navController.navigate(Routes.MY_STATIONS) },
                onNavigateToQrQuests = { navController.navigate(Routes.QUEST_MANAGE) },
                onNavigateToHomework = { navController.navigate(Routes.CREATE_HOMEWORK) },
                onNavigateToReviewHomework = { navController.navigate(Routes.REVIEW_HOMEWORK) },
                onNavigateToPayments = { navController.navigate(Routes.DIRECTOR_PAYMENTS) },
                onNavigateToStaff = { navController.navigate(Routes.STAFF) },
                onNavigateToCreateClub = { navController.navigate(Routes.CREATE_CLUB) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }"""),

    # --- 4c. Новые composable'ы после CREATE_STATION ---
    ("""        composable(Routes.CREATE_STATION) {
            CreateStationScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }""",
     """        composable(Routes.CREATE_STATION) {
            CreateStationScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.STAFF) {
            StaffScreen(
                onBack = { navController.popBackStack() },
                onOpenPermissions = { userId ->
                    navController.navigate(Routes.managePermissions(userId))
                }
            )
        }

        composable(
            route = Routes.MANAGE_PERMISSIONS,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            ManagePermissionsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.QUEST_MANAGE) {
            ManageQuestsScreen(
                onBack = { navController.popBackStack() },
                onOpenQuest = { questId -> navController.navigate(Routes.questEditor(questId)) },
                onCreateQuest = { navController.navigate(Routes.questEditor("new")) }
            )
        }

        composable(
            route = Routes.QUEST_EDITOR,
            arguments = listOf(navArgument("questId") { type = NavType.StringType })
        ) {
            QuestEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.CREATE_HOMEWORK) {
            CreateHomeworkScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.REVIEW_HOMEWORK) {
            ReviewHomeworkScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CREATE_CLUB) {
            CreateClubScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.DIRECTOR_PAYMENTS) {
            DirectorPaymentsScreen(
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate(Routes.CREATE_PAYMENT) }
            )
        }

        composable(Routes.CREATE_PAYMENT) {
            CreatePaymentScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }"""),
])


print()
print("=" * 60)
print("Готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("  firebase deploy --only firestore:rules")
print()
print("Что исправлено:")
print("  - DirectorPaymentsViewModel: [] → emptyList()")
print("  - CreatePaymentViewModel: +import kotlinx.coroutines.flow.first")
print("  - DirectorHomeScreen: +карточка 'Взносы' (8-й ряд)")
print("  - PathfinderNavHost: +9 импортов, +полный DIRECTOR_HOME,")
print("    +8 composable'ов (STAFF, MANAGE_PERMISSIONS, QUEST_MANAGE,")
print("    QUEST_EDITOR, CREATE_HOMEWORK, REVIEW_HOMEWORK, CREATE_CLUB,")
print("    DIRECTOR_PAYMENTS, CREATE_PAYMENT)")
print()
print("Проверка end-to-end:")
print("  1. Директор → Главная → все карточки работают")
print("  2. 'Взносы' → 'Новый взнос' → создать")
print("  3. Родитель → 'Оплата' → видит → 'Отметить как оплачено'")
print("  4. Директор → 'Взносы' → фильтр 'Ждут подтверждения' → 'Подтвердить'")
print()
print("Откат: .bak.<timestamp>")