package com.pathfinder.hub.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pathfinder.hub.ui.navigation.Routes

@Composable
fun RoleHomeScreen(
    navController: NavHostController,
    viewModel: RoleRouterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isLoading, state.role, state.errorMessage) {
        if (state.isLoading) return@LaunchedEffect
        if (state.errorMessage != null) return@LaunchedEffect

        val route = Routes.homeForRole(state.role)
        if (route == null) {
            // Неизвестная роль — покажем ошибку (см. UI ниже), НЕ teen.
            return@LaunchedEffect
        }

        navController.navigate(route) {
            popUpTo(Routes.ROLE_HOME) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else if (state.errorMessage != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Ошибка: ${state.errorMessage}",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }) {
                    Text("Вернуться ко входу")
                }
            }
        } else if (Routes.homeForRole(state.role) == null) {
            // Роль не распознана — не ведём на teen.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Неизвестная роль: ${state.role ?: "—"}",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Обратитесь к администратору клуба",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }) {
                    Text("Вернуться ко входу")
                }
            }
        } else {
            CircularProgressIndicator()
        }
    }
}
