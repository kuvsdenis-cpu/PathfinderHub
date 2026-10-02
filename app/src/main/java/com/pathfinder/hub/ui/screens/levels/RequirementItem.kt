package com.pathfinder.hub.ui.screens.levels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun RequirementItem(
    requirement: RequirementUiModel,
    isLeader: Boolean,
    onToggle: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val (icon, tint, textDecoration) = when (requirement.status) {
        "approved" -> Triple(Icons.Default.CheckCircle, Color.Green, TextDecoration.LineThrough)
        "submitted" -> Triple(Icons.Default.Send, Color.Blue, TextDecoration.None)
        else -> Triple(Icons.Default.RadioButtonUnchecked, Color.Gray, TextDecoration.None)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .clickable(enabled = !isLeader) { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Status",
            tint = tint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = requirement.text,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = textDecoration,
            modifier = Modifier.weight(1f)
        )

        if (isLeader && requirement.status == "submitted") {
            Button(
                onClick = onApprove,
                modifier = Modifier.padding(start = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("✓")
            }
            Button(
                onClick = onReject,
                modifier = Modifier.padding(start = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("✗")
            }
        }
    }
}