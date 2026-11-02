package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.domain.permissions.TilePermissions
import com.pathfinder.hub.ui.screens.groups.ManageCustomGroupsContent
import com.pathfinder.hub.ui.screens.home.director.ClubContextViewModel
import com.pathfinder.hub.ui.screens.home.director.ClubMembersContent
import com.pathfinder.hub.ui.screens.home.director.DirectorInvitesContent
import com.pathfinder.hub.ui.screens.home.director.scout.ScoutProgressScreen
import com.pathfinder.hub.ui.screens.home.director.staff.StaffContent
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorPeopleScreen(
    onBack: () -> Unit,
    onOpenPermissions: (String) -> Unit = {},
    onOpenDM: (dmId: String, otherUid: String) -> Unit = { _, _ -> },
    onOpenMember: (userId: String) -> Unit = {},
    onOpenGroup: (String) -> Unit = {},
    onCreateGroup: () -> Unit = {},
    onOpenScout: (String) -> Unit = {},
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val clubState by clubContextViewModel.state.collectAsState()
    val user = clubState.currentUser
    var tab by remember { mutableIntStateOf(0) }

    val tabs = buildList {
        if (TilePermissions.isVisible(user, TilePermissions.MEMBERS)) {
            add("Сотрудники")
            add("Участники")
        }
        if (TilePermissions.isVisible(user, TilePermissions.INVITES)) add("Инвайты")
        if (TilePermissions.isVisible(user, TilePermissions.GROUPS)) add("Группы")
        if (TilePermissions.isVisible(user, TilePermissions.MEMBERS)) add("Родители")
        if (TilePermissions.isVisible(user, TilePermissions.SCOUT_PROGRESS)) add("Прогресс")
    }

    LaunchedEffect(tabs.size) {
        if (tab >= tabs.size) tab = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Люди") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(tabs) { idx, title ->
                    SectionTile(
                        icon = iconFor(title),
                        title = title,
                        onClick = { tab = idx },
                        selected = tab == idx,
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tabs.getOrNull(tab)) {
                    "Сотрудники" -> StaffContent(
                        onBack = onBack,
                        onOpenPermissions = onOpenPermissions,
                    )
                    "Участники" -> ClubMembersContent(
                        onOpenDM = onOpenDM,
                        onOpenMember = onOpenMember,
                    )
                    "Инвайты" -> DirectorInvitesContent()
                    "Группы" -> ManageCustomGroupsContent(
                        onOpenGroup = onOpenGroup,
                        onCreateGroup = onCreateGroup,
                    )
                    "Родители" -> ManageParentsScreen()
                    "Прогресс" -> ScoutProgressScreen(onOpenScout = onOpenScout)
                    else -> {}
                }
            }
        }
    }
}

private fun iconFor(title: String): ImageVector = when (title) {
    "Сотрудники" -> Icons.Default.Badge
    "Участники" -> Icons.Default.Groups
    "Инвайты" -> Icons.Default.Email
    "Группы" -> Icons.Default.Folder
    "Родители" -> Icons.Default.Person
    "Прогресс" -> Icons.Default.Star
    "Межклубные" -> Icons.Default.Public
    else -> Icons.Default.Groups
}

@Composable
private fun SectionTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = PathfinderBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}