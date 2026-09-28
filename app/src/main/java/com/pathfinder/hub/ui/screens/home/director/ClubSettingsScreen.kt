package com.pathfinder.hub.ui.screens.home.director
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubSettingsScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Настройки клуба") }, navigationIcon = { IconButton(onClick = onBack) { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = PathfinderBlue, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary))
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("Настройки клуба (в разработке)") }
    }
}