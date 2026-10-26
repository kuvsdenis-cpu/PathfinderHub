package com.pathfinder.hub.ui.screens.home.director.staff

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.domain.permissions.PermissionRegistry
import com.pathfinder.hub.ui.components.CollapsibleSection
import com.pathfinder.hub.ui.screens.home.director.ClubContextViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    onBack: () -> Unit,
    onOpenPermissions: (String) -> Unit,
    onOpenInvites: () -> Unit = {},
    viewModel: StaffViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val clubState by clubContextViewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сотрудники") },
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            StaffContent(
                onBack = onBack,
                onOpenPermissions = onOpenPermissions,
                onOpenInvites = onOpenInvites,
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                clubContextViewModel = clubContextViewModel,
            )
        }
    }
}

@Composable
fun StaffContent(
    onBack: () -> Unit,
    onOpenPermissions: (String) -> Unit,
    onOpenInvites: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StaffViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clubState by clubContextViewModel.state.collectAsState()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.directors.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Person, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Нет сотрудников", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.directors, key = { it.id }) { d ->
                    val isOwner = state.currentUser?.isOwnerClub == true
                    val canEditPermissions = isOwner && !d.isOwnerClub && d.id != state.currentUserId
                    StaffRow(
                        user = d,
                        isMe = d.id == state.currentUserId,
                        canEditPermissions = canEditPermissions,
                        onClick = {
                            if (canEditPermissions) onOpenPermissions(d.id)
                        },
                    )
                }

                val me = state.currentUser
                val candidates = state.directors.filter {
                    !it.isOwnerClub && it.id != state.currentUserId
                }
                if (me?.isOwnerClub == true) {
                    item {
                        Card(
                            onClick = {
                                if (candidates.isEmpty()) viewModel.openInviteDialog()
                                else viewModel.openTransferDialog()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.SwapHoriz, null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Передать клуб",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        "Выбрать нового владельца",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (me?.isOwnerClub == true) {
                    item {
                        Card(
                            onClick = viewModel::openInviteDialog,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Email, null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Пригласить нового директора",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        "Создать инвайт для передачи клуба",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (me?.isOwnerClub == true) {
                    item {
                        Card(
                            onClick = viewModel::openStaffInviteDialog,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person, null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Пригласить сотрудника",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "Наставник, секретарь или со-директор",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (me?.isOwnerClub == true) {
                    item {
                        Card(
                            onClick = viewModel::openTransferByEmailDialog,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Email, null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Передать клубу по email",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        "Если пользователь уже зарегистрирован",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- Диалог выбора ----------
    if (state.showTransferDialog) {
        val candidates = state.directors.filter {
            !it.isOwnerClub && it.id != state.currentUserId
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissTransferDialog,
            title = { Text("Передать клуб") },
            text = {
                Column {
                    Text(
                        "Выберите сотрудника, который станет новым владельцем клуба. " +
                                "Вы выпадете из клуба и потеряете к нему доступ.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    candidates.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.transferTarget?.id == c.id,
                                onClick = { viewModel.selectTarget(c) }
                            )
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${c.firstName} ${c.lastName}".trim(),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    when (c.role) {
                                        "director" -> "Со-директор"
                                        "instructor" -> "Наставник"
                                        "secretary" -> "Секретарь"
                                        else -> c.role
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    state.transferError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::requestConfirm,
                    enabled = state.transferTarget != null,
                ) { Text("Далее") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissTransferDialog) { Text("Отмена") }
            }
        )
    }

    // ---------- Диалог подтверждения ----------
    if (state.showConfirmDialog) {
        val t = state.transferTarget
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmDialog,
            title = { Text("Передать клуб?") },
            text = {
                Text(
                    "Новый владелец: " +
                            (t?.let { "${it.firstName} ${it.lastName}".trim() } ?: "") +
                            ".\n\nВы потеряете доступ к клубу и выпадете из него. " +
                            "Действие необратимо."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmTransfer(onSuccess = onBack) },
                ) { Text("Передать", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirmDialog) { Text("Отмена") }
            }
        )
    }

    // ---------- Диалог приглашения нового директора ----------
    if (state.showInviteDialog) {
        val ctx = LocalContext.current
        val code = state.createdInviteCode
        if (code == null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissInviteDialog,
                title = { Text("Пригласить нового директора") },
                text = {
                    Column {
                        Text(
                            "Введите email нового человека. Мы создадим инвайт-код, " +
                                    "который вы отправите ему. После его регистрации клуб " +
                                    "перейдёт к нему, а вы выпадете из клуба.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.inviteEmail,
                            onValueChange = viewModel::onInviteEmailChange,
                            label = { Text("Email нового директора") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        state.inviteError?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text(err, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::createTransferInvite) {
                        Text("Создать код")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissInviteDialog) { Text("Отмена") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = viewModel::dismissInviteDialog,
                title = { Text("Инвайт создан") },
                text = {
                    Column {
                        Text(
                            "Отправьте этот код человеку. Он должен ввести его при регистрации.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Приглашение стать директором клуба Pathfinder Hub.\n\n" +
                                        "Инвайт-код: $code\n\n" +
                                        "Скачайте приложение и введите код при регистрации. " +
                                        "Код действует 7 дней."
                            )
                        }
                        ctx.startActivity(Intent.createChooser(intent, "Поделиться"))
                    }) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Поделиться")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissInviteDialog) { Text("Готово") }
                }
            )
        }
    }

    // ---------- Диалог приглашения сотрудника (2D) ----------
    if (state.showStaffInviteDialog) {
        val code = state.createdStaffInviteCode
        if (code == null) {
            StaffInviteDialog(
                role = state.staffInviteRole,
                permissions = state.staffInvitePermissions,
                children = state.childrenForInvite,
                selectedChildId = state.staffInviteChildId,
                error = state.staffInviteError,
                onRoleSelect = viewModel::selectStaffInviteRole,
                onTogglePermission = viewModel::toggleStaffPermission,
                onSelectChild = viewModel::selectStaffInviteChild,
                onDismiss = viewModel::dismissStaffInviteDialog,
                onConfirm = viewModel::createStaffInvite,
            )
        } else {
            CreatedCodeDialog(
                code = code,
                onDismiss = viewModel::dismissStaffInviteDialog,
                onOpenInvites = {
                    viewModel.dismissStaffInviteDialog()
                    onOpenInvites()
                },
            )
        }
    }

    // ---------- Диалог передачи по email (2E) ----------
    if (state.showTransferByEmailDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissTransferByEmailDialog,
            title = { Text("Передать клубу по email") },
            text = {
                Column {
                    Text(
                        "Введите email человека. Если он уже зарегистрирован — " +
                                "клуб сразу перейдёт к нему. Если нет — используйте " +
                                "«Пригласить нового директора».",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.transferEmail,
                        onValueChange = viewModel::onTransferEmailChange,
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.transferByEmailError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.transferByEmail(onSuccess = onBack) }) {
                    Text("Передать", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissTransferByEmailDialog) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StaffInviteDialog(
    role: String,
    permissions: Set<String>,
    children: List<UserEntity>,
    selectedChildId: String?,
    error: String?,
    onRoleSelect: (String) -> Unit,
    onTogglePermission: (String) -> Unit,
    onSelectChild: (String?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пригласить сотрудника") },
        text = {
            Column {
                Text("Роль", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "instructor" to "Наставник",
                        "secretary" to "Секретарь",
                        "director" to "Со-директор",
                        "parent" to "Родитель",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = role == value,
                            onClick = { onRoleSelect(value) },
                            label = { Text(label) },
                        )
                    }
                }

                if (role == "parent") {
                    Spacer(Modifier.height(12.dp))
                    Text("Ребёнок (для привязки)", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    if (children.isEmpty()) {
                        Text(
                            "В клубе нет участников-детей",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            children.forEach { child ->
                                FilterChip(
                                    selected = selectedChildId == child.id,
                                    onClick = {
                                        onSelectChild(if (selectedChildId == child.id) null else child.id)
                                    },
                                    label = { Text("${child.firstName} ${child.lastName}".trim()) },
                                )
                            }
                        }
                    }
                }

                if (role == "director") {
                    Spacer(Modifier.height(12.dp))
                    Text("Права со-директора", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    PermissionRegistry.GROUPS.forEach { group ->
                        val selectedCount = group.permissions.count { it.key in permissions }
                        CollapsibleSection(
                            title = group.title,
                            badge = if (selectedCount > 0) "$selectedCount из ${group.permissions.size}" else null,
                            initiallyExpanded = false,
                        ) {
                            group.permissions.forEach { perm ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = perm.key in permissions,
                                        onCheckedChange = { onTogglePermission(perm.key) }
                                    )
                                    Text(perm.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Создать код") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatedCodeDialog(
    code: String,
    onDismiss: () -> Unit,
    onOpenInvites: () -> Unit = {},
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Инвайт создан") },
        text = {
            Column {
                Text(
                    "Отправьте этот код сотруднику. Он должен ввести его при регистрации.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Text(code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Приглашение в клуб Pathfinder Hub.\n\n" +
                                "Инвайт-код: $code\n\n" +
                                "Скачайте приложение и введите код при регистрации."
                    )
                }
                ctx.startActivity(Intent.createChooser(intent, "Поделиться"))
            }) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(4.dp))
                Text("Поделиться")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenInvites) { Text("Список инвайтов") }
                TextButton(onClick = onDismiss) { Text("Готово") }
            }
        }
    )
}

@Composable
private fun StaffRow(
    user: UserEntity,
    isMe: Boolean,
    canEditPermissions: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = canEditPermissions,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (user.isOwnerClub) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                null,
                tint = PathfinderBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${user.firstName} ${user.lastName}".trim() + if (isMe) " (вы)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val roleLabel = when {
                    user.isOwnerClub -> "Владелец клуба"
                    user.role == "director" -> "Со-директор · ${user.directorPermissions.size} прав"
                    user.role == "instructor" -> "Наставник"
                    user.role == "secretary" -> "Секретарь"
                    else -> user.role
                }
                Text(
                    roleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val chipLabel = when {
                user.isOwnerClub -> "Owner"
                user.role == "director" -> "Co-dir"
                user.role == "instructor" -> "Instr"
                user.role == "secretary" -> "Secr"
                else -> user.role
            }
            AssistChip(onClick = {}, label = { Text(chipLabel) })
            if (canEditPermissions) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }
}
