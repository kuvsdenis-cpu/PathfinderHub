#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Фикс зависания ContentManagerScreen.
Причина: flow.collect без выхода из бесконечного Flow.
Решение: .first().
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

CONTENT = '''package com.pathfinder.hub.ui.learning.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.repository.LevelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LevelWithCount(
    val level: LevelEntity,
    val requirementCount: Int,
    val sectionCount: Int
)

data class ContentManagerUiState(
    val isLoading: Boolean = true,
    val levels: List<LevelWithCount> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ContentManagerViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContentManagerUiState())
    val state: StateFlow<ContentManagerUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                // .first() берёт первый снапшот и отписывается.
                // Без .first() collect остаётся вечно, потому что Room Flow бесконечный.
                val levels = levelRepository.observeLevels().first()

                val enriched = levels
                    .sortedBy { it.order }
                    .map { lvl ->
                        val reqs = levelRepository.getRequirements(lvl.id)
                        val sections = levelRepository.getSections(lvl.id)
                        LevelWithCount(
                            level = lvl,
                            requirementCount = reqs.size,
                            sectionCount = sections.size
                        )
                    }

                _state.update {
                    it.copy(isLoading = false, levels = enriched)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }
}
'''


def main():
    print("=" * 60)
    print("  Fix: ContentManagerScreen вечный loading")
    print("=" * 60)

    f = PKG / "ui/learning/content/ContentManagerViewModel.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        sys.exit(1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(CONTENT, encoding="utf-8")
    print("✅ ContentManagerViewModel.kt переписан (.bak сохранён)")
    print("\nСборка: .\\gradlew assembleDebug")
    print("=" * 60)


if __name__ == "__main__":
    main()