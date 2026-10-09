#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub — Сессия B+C.2.
1. Открытие ссылок/файлов материалов.
2. Прикрепление файла к ответу задания (навигация через FileUploadScreen).
3. Загрузка файла в редакторе материалов (прямой picker + StorageRepository).

Патчит 4 файла + создаёт 1 утилитарный.
"""

import shutil
import sys
from pathlib import Path


def find_project_root() -> Path:
    here = Path.cwd()
    for p in [here] + list(here.parents):
        if (p / "settings.gradle.kts").exists() or (p / "settings.gradle").exists():
            return p
    print("❌ Не найден корень проекта.")
    sys.exit(1)


ROOT = find_project_root()
PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "pathfinder" / "hub"

if not PKG.exists():
    print(f"❌ Не найден пакет: {PKG}")
    sys.exit(1)


# ============================================================
# НОВЫЙ ФАЙЛ: FileIntentUtil.kt
# ============================================================
FILES = {}

FILES["ui/learning/FileIntentUtil.kt"] = '''package com.pathfinder.hub.ui.learning

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Утилиты для открытия ссылок и файлов во внешних приложениях.
 */
object FileIntentUtil {

    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    fun openFile(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), guessMimeType(url))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
        }
    }

    fun guessMimeType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".doc") -> "application/msword"
            lower.endsWith(".docx") ->
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.endsWith(".xls") -> "application/vnd.ms-excel"
            lower.endsWith(".xlsx") ->
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
            lower.endsWith(".pptx") ->
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".txt") -> "text/plain"
            else -> "*/*"
        }
    }
}
'''


# ============================================================
# ЗАПИСЬ ФАЙЛОВ
# ============================================================
def write_files() -> None:
    print(f"📁 Пакет: {PKG}\n")
    for rel, content in FILES.items():
        target = PKG / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            shutil.copy2(target, target.with_suffix(target.suffix + ".bak"))
            print(f"⚠️  Перезаписываю {rel}")
        target.write_text(content, encoding="utf-8")
        print(f"✅ {rel}")


# ============================================================
# ПАТЧ 1 — RequirementContentSection.kt
# ============================================================
def patch_content_section() -> bool:
    print("\n🔧 Патчу RequirementContentSection.kt...")
    f = PKG / "ui/learning/RequirementContentSection.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "FileIntentUtil" in text and "onAttachFile" in text:
        print("⚠️  Уже пропатчен")
        return True

    # 1) Импорты
    if "FileIntentUtil" not in text:
        import_anchor = "import androidx.hilt.navigation.compose.hiltViewModel"
        if import_anchor not in text:
            print("❌ Нет якоря импорта hiltViewModel")
            return False
        text = text.replace(
            import_anchor,
            import_anchor + "\nimport androidx.compose.ui.platform.LocalContext",
            1
        )

    # 2) Параметр onAttachFile
    if "onAttachFile: (taskId: String) -> Unit" not in text:
        sig_anchor = '''fun RequirementContentSection(
    onCompletionChanged: (hasRequired: Boolean, allDone: Boolean) -> Unit = { _, _ -> },
    viewModel: RequirementContentViewModel = hiltViewModel(),
) {'''
        sig_new = '''fun RequirementContentSection(
    onCompletionChanged: (hasRequired: Boolean, allDone: Boolean) -> Unit = { _, _ -> },
    onAttachFile: (taskId: String) -> Unit = {},
    viewModel: RequirementContentViewModel = hiltViewModel(),
) {'''
        if sig_anchor not in text:
            print("❌ Не найден якорь сигнатуры")
            return False
        text = text.replace(sig_anchor, sig_new, 1)

    # 3) LocalContext
    if "val context = LocalContext.current" not in text:
        ctx_anchor = '''    val state by viewModel.state.collectAsState()
    var openTask by remember { mutableStateOf<RequirementTaskEntity?>(null) }'''
        ctx_new = '''    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var openTask by remember { mutableStateOf<RequirementTaskEntity?>(null) }'''
        if ctx_anchor not in text:
            print("❌ Не найден якорь состояний")
            return False
        text = text.replace(ctx_anchor, ctx_new, 1)

    # 4) onOpenLink / onOpenFile
    text = text.replace(
        'onOpenLink = { /* TODO: открыть в браузере через Intent */ },\n                    onOpenFile = { /* TODO: открыть файл */ }',
        'onOpenLink = { url -> FileIntentUtil.openUrl(context, url) },\n                    onOpenFile = { url -> FileIntentUtil.openFile(context, url) }',
    )

    # 5) onAttachFile в TaskAnswerSheet
    text = text.replace(
        '''            onAttachFile = {
                // TODO: вынести через колбэк наверх в навигацию
            }''',
        '''            onAttachFile = { onAttachFile(t.id) }'''
    )

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ RequirementContentSection.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 2 — RequirementDetailScreen.kt
# ============================================================
def patch_requirement_detail() -> bool:
    print("\n🔧 Патчу RequirementDetailScreen.kt...")
    f = PKG / "ui/screens/levels/RequirementDetailScreen.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "onAttachTaskFile" in text:
        print("⚠️  Уже пропатчен")
        return True

    # 1) Новый параметр в сигнатуре
    sig_anchor = '''    onOpenEditor: (String, String) -> Unit = { _, _ -> },
    savedStateHandle: SavedStateHandle,'''
    sig_new = '''    onOpenEditor: (String, String) -> Unit = { _, _ -> },
    onAttachTaskFile: (requirementId: String, taskId: String) -> Unit = { _, _ -> },
    savedStateHandle: SavedStateHandle,'''
    if sig_anchor not in text:
        print("❌ Не найден якорь сигнатуры (ожидался onOpenEditor)")
        return False
    text = text.replace(sig_anchor, sig_new, 1)

    # 2) Прокидка в RequirementContentSection
    sec_anchor = '''                    RequirementContentSection(
                        onCompletionChanged = { hasReq, allDone ->
                            hasRequiredTasks = hasReq
                            allRequiredDone = allDone
                        }
                    )'''
    sec_new = '''                    RequirementContentSection(
                        onCompletionChanged = { hasReq, allDone ->
                            hasRequiredTasks = hasReq
                            allRequiredDone = allDone
                        },
                        onAttachFile = { taskId ->
                            onAttachTaskFile(state.requirement?.id ?: "", taskId)
                        }
                    )'''
    if sec_anchor not in text:
        print("❌ Не найден якорь врезки RequirementContentSection")
        return False
    text = text.replace(sec_anchor, sec_new, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ RequirementDetailScreen.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 3 — PathfinderNavHost.kt
# ============================================================
def patch_navhost() -> bool:
    print("\n🔧 Патчу PathfinderNavHost.kt...")
    f = PKG / "ui/navigation/PathfinderNavHost.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "onAttachTaskFile" in text:
        print("⚠️  Уже пропатчен")
        return True

    old_call = '''                onOpenEditor = { lvlId, requirementId ->
                    navController.navigate(Routes.requirementEditor(lvlId, requirementId))
                },
                savedStateHandle = backStackEntry.savedStateHandle
            )'''
    new_call = '''                onOpenEditor = { lvlId, requirementId ->
                    navController.navigate(Routes.requirementEditor(lvlId, requirementId))
                },
                onAttachTaskFile = { requirementId, taskId ->
                    navController.navigate(
                        Routes.fileUpload(
                            contextType = "TASK_ANSWER",
                            contextId = "$requirementId|$taskId"
                        )
                    )
                },
                savedStateHandle = backStackEntry.savedStateHandle
            )'''
    if old_call not in text:
        print("❌ Не найден якорь вызова RequirementDetailScreen с onOpenEditor")
        return False
    text = text.replace(old_call, new_call, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ PathfinderNavHost.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 4 — RequirementEditorViewModel.kt (StorageRepository + upload)
# ============================================================
def patch_editor_viewmodel() -> bool:
    print("\n🔧 Патчу RequirementEditorViewModel.kt...")
    f = PKG / "ui/learning/editor/RequirementEditorViewModel.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "uploadMaterialFile" in text:
        print("⚠️  Уже пропатчен")
        return True

    # 1) Импорты
    import_anchor = "import android.util.Log"
    if import_anchor not in text:
        print("❌ Нет якоря import android.util.Log")
        return False

    new_imports = (
        "import android.content.Context\n"
        "import android.net.Uri\n"
        "import com.pathfinder.hub.data.repository.StorageRepository\n"
        "import dagger.hilt.android.qualifiers.ApplicationContext\n"
        "import kotlinx.coroutines.Dispatchers\n"
        "import kotlinx.coroutines.withContext\n"
        "import java.io.File\n"
        "import java.util.UUID\n"
    )
    text = text.replace(import_anchor, new_imports + import_anchor, 1)

    # 2) Конструктор — добавить Context + StorageRepository
    ctor_anchor = '''class RequirementEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val contentRepo: RequirementContentRepository,
) : ViewModel() {'''
    ctor_new = '''class RequirementEditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val contentRepo: RequirementContentRepository,
    private val storage: StorageRepository,
) : ViewModel() {'''
    if ctor_anchor not in text:
        print("❌ Не найден якорь конструктора")
        return False
    text = text.replace(ctor_anchor, ctor_new, 1)

    # 3) Поле uploadedUrl в состоянии
    state_anchor = '''    val errorMessage: String? = null,
    val infoMessage: String? = null,
)'''
    state_new = '''    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val uploadedMaterialUrl: String? = null,
    val isUploading: Boolean = false,
)'''
    if state_anchor not in text:
        print("❌ Не найден якорь RequirementEditorUiState")
        return False
    text = text.replace(state_anchor, state_new, 1)

    # 4) Метод uploadMaterialFile + uriToFile
    method_anchor = '''    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }'''
    method_new = '''    // ==================== ЗАГРУЗКА ФАЙЛА МАТЕРИАЛА ====================

    fun uploadMaterialFile(uri: Uri) {
        if (!_state.value.canEdit) return
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, errorMessage = null) }
            try {
                val file = uriToFile(uri)
                if (file == null) {
                    _state.update { it.copy(isUploading = false, errorMessage = "Не удалось прочитать файл") }
                    return@launch
                }
                val ext = file.extension.ifBlank { "bin" }
                val key = "requirements/$requirementId/materials/${UUID.randomUUID()}.$ext"
                val r = storage.uploadFile(file, key) { /* progress */ }
                r.fold(
                    onSuccess = { url ->
                        _state.update {
                            it.copy(
                                isUploading = false,
                                uploadedMaterialUrl = url,
                                infoMessage = "Файл загружен"
                            )
                        }
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(isUploading = false, errorMessage = e.message ?: "Ошибка загрузки")
                        }
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isUploading = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }

    fun clearUploadedUrl() {
        _state.update { it.copy(uploadedMaterialUrl = null) }
    }

    private suspend fun uriToFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val input = appContext.contentResolver.openInputStream(uri) ?: return@withContext null
            val file = File(appContext.cacheDir, "upload_${System.currentTimeMillis()}")
            file.outputStream().use { input.copyTo(it) }
            file
        } catch (e: Exception) {
            null
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }'''
    if method_anchor not in text:
        print("❌ Не найден якорь clearMessages()")
        return False
    text = text.replace(method_anchor, method_new, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ RequirementEditorViewModel.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 5 — MaterialEditDialog.kt (picker + кнопка загрузки)
# ============================================================
def patch_material_dialog() -> bool:
    print("\n🔧 Патчу MaterialEditDialog.kt...")
    f = PKG / "ui/learning/editor/MaterialEditDialog.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "onUploadFile" in text:
        print("⚠️  Уже пропатчен")
        return True

    # 1) Импорты
    imp_anchor = "import androidx.compose.runtime.*"
    if imp_anchor not in text:
        print("❌ Нет якоря runtime.*")
        return False

    new_imp = '''import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.dp
'''
    text = text.replace(imp_anchor, new_imp + imp_anchor, 1)

    # 2) Сигнатура — новые параметры
    sig_anchor = '''fun MaterialEditDialog(
    existing: RequirementMaterialEntity?,
    onDismiss: () -> Unit,
    onSave: (type: String, title: String, content: String) -> Unit,
) {
    var type by remember { mutableStateOf(existing?.type ?: "text") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }'''
    sig_new = '''fun MaterialEditDialog(
    existing: RequirementMaterialEntity?,
    uploadedUrl: String? = null,
    isUploading: Boolean = false,
    onDismiss: () -> Unit,
    onUploadFile: (Uri) -> Unit = {},
    onUploadedUrlConsumed: () -> Unit = {},
    onSave: (type: String, title: String, content: String) -> Unit,
) {
    var type by remember { mutableStateOf(existing?.type ?: "text") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }

    // Подхватываем загруженный URL
    LaunchedEffect(uploadedUrl) {
        if (!uploadedUrl.isNullOrBlank()) {
            content = uploadedUrl
            if (type !in listOf("file", "image")) type = "file"
            onUploadedUrlConsumed()
        }
    }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(onUploadFile) }'''
    if sig_anchor not in text:
        print("❌ Не найден якорь сигнатуры MaterialEditDialog")
        return False
    text = text.replace(sig_anchor, sig_new, 1)

    # 3) Кнопка загрузки после поля content
    field_anchor = '''                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = {
                        Text(
                            when (type) {
                                "link" -> "URL"
                                "image" -> "URL картинки"
                                "file" -> "URL файла"
                                else -> "Текст"
                            }
                        )
                    },
                    minLines = if (type == "text") 4 else 1,
                    modifier = Modifier.fillMaxWidth()
                )'''
    field_new = '''                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = {
                        Text(
                            when (type) {
                                "link" -> "URL"
                                "image" -> "URL картинки"
                                "file" -> "URL файла"
                                else -> "Текст"
                            }
                        )
                    },
                    minLines = if (type == "text") 4 else 1,
                    modifier = Modifier.fillMaxWidth()
                )

                if (type == "file" || type == "image") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        enabled = !isUploading,
                        onClick = {
                            pickFile.launch(if (type == "image") "image/*" else "*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (content.isBlank())
                                Icons.Default.CloudUpload
                            else
                                Icons.Default.Refresh,
                            contentDescription = null
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(
                            when {
                                isUploading -> "Загрузка…"
                                content.isBlank() -> " Загрузить файл"
                                else -> " Заменить файл"
                            }
                        )
                    }
                }'''
    if field_anchor not in text:
        print("❌ Не найден якорь поля content")
        return False
    text = text.replace(field_anchor, field_new, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ MaterialEditDialog.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 6 — RequirementEditorScreen.kt (прокидка onUploadFile)
# ============================================================
def patch_editor_screen() -> bool:
    print("\n🔧 Патчу RequirementEditorScreen.kt...")
    f = PKG / "ui/learning/editor/RequirementEditorScreen.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "onUploadFile" in text:
        print("⚠️  Уже пропатчен")
        return True

    # Создание
    old_create = '''    if (creatingMaterial) {
        MaterialEditDialog(
            existing = null,
            onDismiss = { creatingMaterial = false },
            onSave = { type, title, content ->
                viewModel.saveMaterial(null, type, title, content, state.materials.size)
                creatingMaterial = false
            }
        )
    }'''
    new_create = '''    if (creatingMaterial) {
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
    }'''
    if old_create not in text:
        print("❌ Не найден якорь создания материала")
        return False
    text = text.replace(old_create, new_create, 1)

    # Редактирование
    old_edit = '''    editingMaterial?.let { m ->
        MaterialEditDialog(
            existing = m,
            onDismiss = { editingMaterial = null },
            onSave = { type, title, content ->
                viewModel.saveMaterial(m, type, title, content, m.orderIndex)
                editingMaterial = null
            }
        )
    }'''
    new_edit = '''    editingMaterial?.let { m ->
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
    }'''
    if old_edit not in text:
        print("❌ Не найден якорь редактирования материала")
        return False
    text = text.replace(old_edit, new_edit, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ RequirementEditorScreen.kt пропатчен")
    return True


# ============================================================
# MAIN
# ============================================================
def main():
    print("=" * 60)
    print("  Pathfinder Hub — Сессия B+C.2")
    print("=" * 60)

    write_files()

    results = {
        "RequirementContentSection.kt": patch_content_section(),
        "RequirementDetailScreen.kt":   patch_requirement_detail(),
        "PathfinderNavHost.kt":         patch_navhost(),
        "RequirementEditorViewModel.kt": patch_editor_viewmodel(),
        "MaterialEditDialog.kt":        patch_material_dialog(),
        "RequirementEditorScreen.kt":   patch_editor_screen(),
    }

    print("\n" + "=" * 60)
    for name, ok in results.items():
        print(f"  {'✅' if ok else '❌'} {name}")

    if all(results.values()):
        print("\n✅ Все патчи применены.")
        print("\nСборка: .\\gradlew assembleDebug")
        print("\nТест:")
        print("  1. Материал типа link → тап на URL → браузер")
        print("  2. Материал типа file/image → тап → внешний просмотрщик")
        print("  3. Задание типа file → «Прикрепить файл» → FileUploadScreen")
        print("  4. Редактор → создать материал → «Загрузить файл» → picker → Yandex")
    else:
        print("\n⚠️  Часть патчей не удалась. Смотри .bak.")

    print("=" * 60)


if __name__ == "__main__":
    main()