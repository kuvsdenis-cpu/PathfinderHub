package com.pathfinder.hub.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pathfinder.hub.ui.navigation.Routes

@Composable
fun RoleHomeScreen(
    navController: NavHostController,
    viewModel: RoleRouterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.role) {
        if (!state.isLoading && state.role != null) {
            val route = when (state.role) {
                "director" -> Routes.DIRECTOR_HOME
                "teen", "parent" -> Routes.TEEN_HOME
                else -> Routes.TEEN_HOME // Запасной вариант
            }

            navController.navigate(route) {
                popUpTo(Routes.ROLE_HOME) { inclusive = true }
            }
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
        } else {
            CircularProgressIndicator()
        }
    }
}