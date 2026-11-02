package com.pathfinder.hub.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.data.local.entity.core.ClubEntity

/**
 * Вкладки клубов. Рендерится только если у пользователя >1 клуб.
 *
 * @param clubs  список клубов пользователя
 * @param selectedClubId  id активного клуба (user.clubId)
 * @param onSelect  при тапе — сохранить активный клуб
 */
@Composable
fun ClubTabsRow(
    clubs: List<ClubEntity>,
    selectedClubId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (clubs.size <= 1) return

    val selectedIndex = clubs
        .indexOfFirst { it.id == selectedClubId }
        .coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        clubs.forEach { club ->
            Tab(
                selected = club.id == selectedClubId,
                onClick = { onSelect(club.id) },
                text = {
                    Text(
                        club.name,
                        maxLines = 1,
                        fontWeight = if (club.id == selectedClubId)
                            FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}
