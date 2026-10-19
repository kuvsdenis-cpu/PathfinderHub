package com.pathfinder.hub.ui.learning.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequirementEditorScreen(
    onBack: () -> Unit,
    viewModel: RequirementEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

    var editingMaterial by remember { mutableStateOf<RequirementMaterialEntity?>(null) }
    var creatingMaterial by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<RequirementTaskEntity?>(null) }
    var creatingTask by remember { mutableStateOf(false) }
    var flaggingMaterial by remember { mutableStateOf<RequirementMaterialEntity?>(null) }
    var flaggingTask by remember { mutableStateOf<RequirementTaskEntity?>(null) }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактор требования") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.canEdit) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (tab == 0) creatingMaterial = true else creatingTask = true
                    },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text(if (tab == 0) "Материал" else "Задание") }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Материалы (${state.materials.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Задания (${state.tasks.size})") })
            }

            when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(state.materials, key = { it.id }) { m ->
                        RowItemMaterial(
                            m = m,
                            canEdit = state.canEdit,
                            canFlag = state.canFlag,
                            onEdit = { editingMaterial = m },
                            onDelete = { viewModel.deleteMaterial(m) },
                            onFlag = { flaggingMaterial = m },
                            onUnflag = { viewModel.unflagMaterial(m) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(state.tasks, key = { it.id }) { t ->
                        RowItemTask(
                            t = t,
                            canEdit = state.canEdit,
                            canFlag = state.canFlag,
                            onEdit = { editingTask = t },
                            onDelete = { viewModel.deleteTask(t) },
                            onFlag = { flaggingTask = t },
                            onUnflag = { viewModel.unflagTask(t) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // ---------------- MATERIAL DIALOGS ----------------
    if (creatingMaterial) {
        MaterialEditDialog(
            existing = null,
            uploadedUrl = state.uploadedMaterialUrl,
            isUploading = state.isUploading,
            onDismiss = { creatingMaterial = false; viewModel.clearUploadedUrl() },
            onUploadFile = { uri -> viewModel.uploadMaterialFile(uri) },
            onUploadedUrlConsumed = { viewModel.clearUploadedUrl() },
            onSave = { type, title, content ->
                viewModel.saveMaterial(null, type, title, content, state.materials.size)
                creatingMaterial = false
                viewModel.clearUploadedUrl()
            }
        )
    }
    editingMaterial?.let { m ->
        MaterialEditDialog(
            existing = m,
            uploadedUrl = state.uploadedMaterialUrl,
            isUploading = state.isUploading,
            onDismiss = { editingMaterial = null; viewModel.clearUploadedUrl() },
            onUploadFile = { uri -> viewModel.uploadMaterialFile(uri) },
            onUploadedUrlConsumed = { viewModel.clearUploadedUrl() },
            onSave = { type, title, content ->
                viewModel.saveMaterial(m, type, title, content, m.orderIndex)
                editingMaterial = null
                viewModel.clearUploadedUrl()
            }
        )
    }
    flaggingMaterial?.let { m ->
        FlagDialog(
            targetTitle = m.title,
            onDismiss = { flaggingMaterial = null },
            onConfirm = { c -> viewModel.flagMaterial(m, c); flaggingMaterial = null }
        )
    }

    // ---------------- TASK DIALOGS ----------------
    if (creatingTask) {
        TaskEditDialog(
            existing = null,
            onDismiss = { creatingTask = false },
            onSave = { type, title, description, options, correct, required ->
                viewModel.saveTask(null, type, title, description, options, correct, required, state.tasks.size)
                creatingTask = false
            }
        )
    }
    editingTask?.let { t ->
        TaskEditDialog(
            existing = t,
            onDismiss = { editingTask = null },
            onSave = { type, title, description, options, correct, required ->
                viewModel.saveTask(t, type, title, description, options, correct, required, t.orderIndex)
                editingTask = null
            }
        )
    }
    flaggingTask?.let { t ->
        FlagDialog(
            targetTitle = t.title,
            onDismiss = { flaggingTask = null },
            onConfirm = { c -> viewModel.flagTask(t, c); flaggingTask = null }
        )
    }
}

@Composable
private fun RowItemMaterial(
    m: RequirementMaterialEntity,
    canEdit: Boolean, canFlag: Boolean,
    onEdit: () -> Unit, onDelete: () -> Unit,
    onFlag: () -> Unit, onUnflag: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("[${m.type}] ${m.title}", style = MaterialTheme.typography.titleMedium)
                if (m.flaggedForEdit) {
                    Text("Помечено: ${m.flagComment ?: "без комментария"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (canEdit) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
            }
            if (canFlag) {
                IconButton(onClick = if (m.flaggedForEdit) onUnflag else onFlag) {
                    Icon(Icons.Default.Flag, null,
                        tint = if (m.flaggedForEdit) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RowItemTask(
    t: RequirementTaskEntity,
    canEdit: Boolean, canFlag: Boolean,
    onEdit: () -> Unit, onDelete: () -> Unit,
    onFlag: () -> Unit, onUnflag: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("[${t.type}] ${t.title}", style = MaterialTheme.typography.titleMedium)
                if (!t.isRequired) Text("Необязательное", style = MaterialTheme.typography.labelSmall)
                if (t.flaggedForEdit) {
                    Text("Помечено: ${t.flagComment ?: "без комментария"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (canEdit) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
            }
            if (canFlag) {
                IconButton(onClick = if (t.flaggedForEdit) onUnflag else onFlag) {
                    Icon(Icons.Default.Flag, null,
                        tint = if (t.flaggedForEdit) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
