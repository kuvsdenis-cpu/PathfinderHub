package com.pathfinder.hub.ui.screens.home.director

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.domain.permissions.PermissionRegistry
import com.pathfinder.hub.ui.components.CollapsibleSection
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorInvitesScreen(
    onBack: () -> Unit,
    viewModel: DirectorInvitesViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val clubState by clubContextViewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инвайты") },
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
            DirectorInvitesContent(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                clubContextViewModel = clubContextViewModel,
            )
        }
    }
}

@Composable
fun DirectorInvitesContent(
    modifier: Modifier = Modifier,
    viewModel: DirectorInvitesViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clubState by clubContextViewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.pullInvites() }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Кого приглашаем?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    "teen" to "Следопыт",
                    "parent" to "Родитель",
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = state.selectedRole == value,
                        onClick = { viewModel.onRoleSelected(value) },
                        label = { Text(label) }
                    )
                }
            }

            if (state.selectedRole in listOf("director", "instructor", "secretary")) {
                val permsTitle = when (state.selectedRole) {
                    "director" -> "Права со-директора"
                    "instructor" -> "Права наставника"
                    "secretary" -> "Права секретаря"
                    else -> "Права"
                }
                val permsHint = when (state.selectedRole) {
                    "director" -> "Не отмечено — со-директор без доступа к этим функциям"
                    "instructor" -> "Не отмечено — наставник без доступа к этим функциям"
                    "secretary" -> "Не отмечено — секретарь без доступа к этим функциям"
                    else -> "Не отмечено — сотрудник без доступа к этим функциям"
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    permsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    permsHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                PermissionRegistry.GROUPS.forEach { group ->
                    val selectedCount = group.permissions.count { it.key in state.selectedPermissions }
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
                                    checked = perm.key in state.selectedPermissions,
                                    onCheckedChange = { viewModel.onPermissionToggle(perm.key) }
                                )
                                Text(perm.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = viewModel::createInvite,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Создать инвайт-код")
                }
            }

            state.lastCreated?.let { invite ->
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Новый код создан", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(invite.code, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val fmt = SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ru"))
                        Text("Действует до: ${fmt.format(invite.expiresAt)}")
                        if (invite.role in listOf("director", "instructor", "secretary")) {
                            Spacer(Modifier.height(4.dp))
                            Text("Права: ${invite.permissions.size}", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, buildShareMessage(invite.code, invite.role))
                            }
                            context.startActivity(Intent.createChooser(intent, "Поделиться"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Поделиться")
                        }
                    }
                }
            }

            state.errorMessage?.let { error ->
                Spacer(Modifier.height(16.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                "Все инвайты клуба",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            state.invites.forEach { invite ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(invite.code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("Роль: ${roleLabel(invite.role)}", style = MaterialTheme.typography.bodyLarge)
                            }
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        when (invite.status) {
                                            "active" -> "Активен"
                                            "used" -> "Использован"
                                            "expired" -> "Истёк"
                                            "revoked" -> "Отозван"
                                            else -> invite.status
                                        }
                                    )
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.resendInvite(invite) },
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Отправить ещё раз")
                        }
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "teen" -> "Следопыт"
    "parent" -> "Родитель"
    "instructor" -> "Наставник"
    "director" -> "Со-директор"
    "secretary" -> "Секретарь"
    else -> role
}

private fun buildShareMessage(code: String, role: String): String = when (role) {
    "director" -> """
        Приглашение со-директора в клуб Pathfinder Hub.

        Инвайт-код: $code

        Скачайте приложение и введите код при регистрации.
        Код действует 7 дней.
    """.trimIndent()
    else -> """
        Приглашение в клуб «Следопыт»!

        Ваш инвайт-код: $code

        Скачайте приложение Pathfinder Hub и введите этот код при регистрации.
        Код действует 7 дней.
    """.trimIndent()
}
