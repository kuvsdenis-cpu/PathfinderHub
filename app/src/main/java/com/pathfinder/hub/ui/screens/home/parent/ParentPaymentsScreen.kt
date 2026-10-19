package com.pathfinder.hub.ui.screens.home.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentPaymentsScreen(
    onBack: () -> Unit,
    viewModel: ParentPaymentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Взносы") },
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
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.payments.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AccountBalance, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Взносов пока нет", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.payments, key = { it.id }) { p ->
                    PaymentCard(
                        payment = p,
                        fmt = fmt,
                        isProcessing = state.processingId == p.id,
                        onPay = { viewModel.markPaid(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(
    payment: PaymentEntity,
    fmt: SimpleDateFormat,
    isProcessing: Boolean,
    onPay: () -> Unit,
) {
    val statusLabel = when (payment.status) {
        "pending" -> "К оплате"
        "paid" -> "Оплачено, ждём подтверждения"
        "confirmed" -> "Оплата подтверждена"
        "overdue" -> "Просрочено"
        else -> payment.status
    }
    val statusColor = when (payment.status) {
        "confirmed" -> PathfinderGreen
        "paid" -> PathfinderBlue
        "overdue" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccountBalance, null,
                    tint = PathfinderBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        payment.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Срок: " + fmt.format(payment.dueDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "%.0f %s".format(payment.amount, payment.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PathfinderBlue
                )
            }

            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = {},
                label = { Text(statusLabel) },
                leadingIcon = {
                    Icon(
                        if (payment.status == "confirmed") Icons.Default.CheckCircle
                        else Icons.Default.Schedule,
                        null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            if (payment.status == "pending" || payment.status == "overdue") {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPay,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Отметить как оплачено")
                    }
                }
            }
        }
    }
}
