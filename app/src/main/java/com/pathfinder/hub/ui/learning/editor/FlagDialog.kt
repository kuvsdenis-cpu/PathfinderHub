package com.pathfinder.hub.ui.learning.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FlagDialog(
    targetTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пометить для правки") },
        text = {
            Column {
                Text("Что не так с «$targetTitle»?")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it },
                    label = { Text("Комментарий (необязательно)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(comment.ifBlank { null }) }) { Text("Пометить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
