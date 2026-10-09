#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub — скрыть кнопку «Редактировать» для teen/parent.
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
# ПАТЧ 1 — RequirementDetailViewModel: userRole в состоянии
# ============================================================
def patch_vm() -> bool:
    print("\n🔧 Патчу RequirementDetailViewModel.kt...")
    f = PKG / "ui/screens/levels/RequirementDetailViewModel.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "userRole" in text:
        print("⚠️  Уже пропатчен")
        return True

    # 1) Импорт UserRepository
    imp_anchor = "import com.pathfinder.hub.data.repository.LevelRepository"
    if imp_anchor not in text:
        print("❌ Нет якоря импорта LevelRepository")
        return False
    text = text.replace(
        imp_anchor,
        imp_anchor + "\nimport com.pathfinder.hub.data.repository.UserRepository",
        1
    )

    # 2) Поле userRole в UiState
    state_anchor = '''    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)'''
    state_new = '''    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val userRole: String = ""
)'''
    if state_anchor not in text:
        print("❌ Не найден якорь RequirementDetailUiState")
        return False
    text = text.replace(state_anchor, state_new, 1)

    # 3) Конструктор + userRepository
    ctor_anchor = '''class RequirementDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val levelRepository: LevelRepository,
    private val sessionManager: SessionManager
) : ViewModel() {'''
    ctor_new = '''class RequirementDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val levelRepository: LevelRepository,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository
) : ViewModel() {'''
    if ctor_anchor not in text:
        print("❌ Не найден якорь конструктора")
        return False
    text = text.replace(ctor_anchor, ctor_new, 1)

    # 4) Загрузка роли в loadRequirement
    load_anchor = '''        viewModelScope.launch {
            val req = levelRepository.getRequirementById(levelId, requirementId)'''
    load_new = '''        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            val role = user?.role ?: ""
            _state.update { it.copy(userRole = role) }

            val req = levelRepository.getRequirementById(levelId, requirementId)'''
    if load_anchor not in text:
        print("❌ Не найден якорь запуска loadRequirement")
        return False
    text = text.replace(load_anchor, load_new, 1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print(f"✅ RequirementDetailViewModel.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 2 — RequirementDetailScreen: условная кнопка
# ============================================================
def patch_screen() -> bool:
    print("\n🔧 Патчу RequirementDetailScreen.kt...")
    f = PKG / "ui/screens/levels/RequirementDetailScreen.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "state.userRole in" in text:
        print("⚠️  Уже пропатчен")
        return True

    old_actions = '''                actions = {
                    IconButton(onClick = { showEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                },'''
    new_actions = '''                actions = {
                    val canOpenEditor = state.userRole in listOf(
                        "director", "conference_admin", "instructor", "secretary"
                    )
                    if (canOpenEditor) {
                        IconButton(onClick = { showEditor = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                        }
                    }
                },'''
    if old_actions not in text:
        print("❌ Не найден якорь actions TopAppBar")
        return False
    text = text.replace(old_actions, new_actions, 1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print(f"✅ RequirementDetailScreen.kt пропатчен")
    return True


def main():
    print("=" * 60)
    print("  Pathfinder Hub — скрыть редактор для teen/parent")
    print("=" * 60)

    r = {
        "RequirementDetailViewModel.kt": patch_vm(),
        "RequirementDetailScreen.kt":    patch_screen(),
    }

    print("\n" + "=" * 60)
    for k, v in r.items():
        print(f"  {'✅' if v else '❌'} {k}")

    if all(r.values()):
        print("\n✅ Готово. Сборка: .\\gradlew assembleDebug")
        print("\nТест:")
        print("  - Под teen: открыть требование → кнопки «Редактировать» нет")
        print("  - Под instructor/director: кнопка есть")
    else:
        print("\n⚠️  Смотри .bak.")
    print("=" * 60)


if __name__ == "__main__":
    main()