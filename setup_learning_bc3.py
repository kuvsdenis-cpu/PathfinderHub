#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub — фикс TASK_ANSWER.
1. Добавить TASK_ANSWER в FileUploadContext.
2. RequirementDetailScreen: проверять result.context вместо contextType.
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


# ============================================================
# ПАТЧ 1 — FileUploadContext.kt: добавить TASK_ANSWER
# ============================================================
def patch_context() -> bool:
    print("\n🔧 Патчу FileUploadContext.kt...")
    f = PKG / "ui/storage/FileUploadContext.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "TASK_ANSWER" in text:
        print("⚠️  Уже пропатчен")
        return True

    anchor = '''    TASK(
        storagePrefix = "tasks",
        allowedMimeTypes = "application/pdf,image/*",
        screenTitle = "Отчёт по задаче"
    ),'''
    if anchor not in text:
        print("❌ Не найден якорь TASK в enum")
        return False

    new_block = '''    TASK(
        storagePrefix = "tasks",
        allowedMimeTypes = "application/pdf,image/*",
        screenTitle = "Отчёт по задаче"
    ),
    TASK_ANSWER(
        storagePrefix = "task_answers",
        allowedMimeTypes = "*/*",
        screenTitle = "Прикрепить файл к заданию"
    ),'''
    text = text.replace(anchor, new_block, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ FileUploadContext.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 2 — RequirementDetailScreen.kt: context вместо contextType
# ============================================================
def patch_detail() -> bool:
    print("\n🔧 Патчу RequirementDetailScreen.kt...")
    f = PKG / "ui/screens/levels/RequirementDetailScreen.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    old_block = '''    LaunchedEffect(pendingAttachment) {
        pendingAttachment?.let { result ->
            when (result.contextType) {
                "TASK_ANSWER" -> {
                    // contextId = "requirementId|taskId"
                    val parts = result.contextId.split("|")
                    val taskId = parts.getOrNull(1)
                    if (taskId != null) {
                        contentViewModel.attachFileToTask(taskId, result.fileUrl)
                    }
                }
                else -> {
                    // LEVEL_REQUIREMENT, HONOR_REQUIREMENT — старая логика
                    viewModel.attachUploadedFile(result)
                }
            }
            savedStateHandle.remove<FileUploadResult>(FileUploadResult.SAVED_STATE_KEY)
        }
    }'''

    new_block = '''    LaunchedEffect(pendingAttachment) {
        pendingAttachment?.let { result ->
            if (result.context == com.pathfinder.hub.ui.storage.FileUploadContext.TASK_ANSWER) {
                // contextId = "requirementId|taskId"
                val taskId = result.contextId.substringAfter("|", "")
                if (taskId.isNotBlank()) {
                    contentViewModel.attachFileToTask(taskId, result.fileUrl)
                }
            } else {
                // LEVEL_REQUIREMENT, HONOR_REQUIREMENT и т.д. — старая логика
                viewModel.attachUploadedFile(result)
            }
            savedStateHandle.remove<FileUploadResult>(FileUploadResult.SAVED_STATE_KEY)
        }
    }'''

    if old_block not in text:
        print("❌ Не найден блок LaunchedEffect(pendingAttachment) с when(result.contextType)")
        return False

    text = text.replace(old_block, new_block, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ RequirementDetailScreen.kt пропатчен")
    return True


def main():
    print("=" * 60)
    print("  Pathfinder Hub — фикс TASK_ANSWER")
    print("=" * 60)

    r = {
        "FileUploadContext.kt":       patch_context(),
        "RequirementDetailScreen.kt": patch_detail(),
    }

    print("\n" + "=" * 60)
    for k, v in r.items():
        print(f"  {'✅' if v else '❌'} {k}")

    if all(r.values()):
        print("\n✅ Готово. Сборка: .\\gradlew assembleDebug")
    else:
        print("\n⚠️  Смотри .bak.")
    print("=" * 60)


if __name__ == "__main__":
    main()