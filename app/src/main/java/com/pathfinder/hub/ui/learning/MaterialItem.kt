package com.pathfinder.hub.ui.learning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity

@Composable
fun MaterialItem(
    material: RequirementMaterialEntity,
    onOpenLink: (String) -> Unit = {},
    onOpenFile: (String) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (material.type) {
                        "link" -> Icons.Default.Link
                        "file" -> Icons.Default.InsertDriveFile
                        "image" -> Icons.Default.Image
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = material.title.ifBlank { material.type.replaceFirstChar { it.uppercase() } },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            when (material.type) {
                "text" -> {
                    Text(
                        text = material.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                "link" -> {
                    Text(
                        text = material.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenLink(material.content) }
                    )
                }
                "image" -> {
                    AsyncImage(
                        model = material.content,
                        contentDescription = material.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                "file" -> {
                    OutlinedButton(
                        onClick = { onOpenFile(material.content) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Открыть файл")
                    }
                }
                else -> {
                    Text(text = material.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
