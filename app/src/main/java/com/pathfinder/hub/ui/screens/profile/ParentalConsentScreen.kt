package com.pathfinder.hub.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalConsentScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var parentEmail by remember { mutableStateOf("") }
    var parentName by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Согласие родителей") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Для использования приложения подросткам (до 18 лет) требуется согласие родителей или опекунов.\n\nВведите email вашего родителя, чтобы отправить ему запрос на согласие.",
                style = MaterialTheme.typography.bodyLarge
            )

            OutlinedTextField(
                value = parentName,
                onValueChange = { parentName = it },
                label = { Text("Имя родителя (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = parentEmail,
                onValueChange = { parentEmail = it },
                label = { Text("Email родителя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    isSending = true
                    val subject = "Согласие на использование приложения Pathfinder Hub"
                    val body = """
                        Здравствуйте, ${parentName.ifBlank { "родитель" }}!

                        Ваш ребенок зарегистрировался в приложении Pathfinder Hub.
                        Для продолжения использования приложения требуется ваше согласие на обработку персональных данных.

                        Пожалуйста, подтвердите свое согласие, ответив на это письмо или связавшись с администрацией клуба.

                        С уважением,
                        Команда Pathfinder Hub
                    """.trimIndent()

                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(parentEmail))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    try {
                        context.startActivity(intent)
                        showSuccess = true
                    } catch (e: Exception) {
                        // В реальном приложении здесь можно показать Toast
                    } finally {
                        isSending = false
                    }
                },
                enabled = parentEmail.isNotBlank() && !isSending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Отправить запрос")
                }
            }

            if (showSuccess) {
                Text(
                    "Письмо сформировано! Не забудьте отправить его из вашего почтового приложения.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
