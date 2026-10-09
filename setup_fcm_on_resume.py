#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Фикс: регистрация FCM-токена при каждой активации MainActivity.
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


def patch_main_activity() -> bool:
    print("\n🔧 Патчу MainActivity.kt...")
    f = PKG / "MainActivity.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "override fun onResume()" in text:
        print("⚠️  Уже пропатчен")
        return True

    # Вставляем onResume после onCreate
    anchor = '''    override fun onNewIntent(intent: Intent) {'''
    if anchor not in text:
        print("❌ Не найден onNewIntent")
        return False

    on_resume_block = '''    override fun onResume() {
        super.onResume()
        // Регистрируем FCM-токен при каждой активации activity.
        // Это нужно, чтобы после логина под новым пользователем
        // токен сохранился в users/{uid}/fcmTokens/{token}.
        lifecycleScope.launch {
            registerFcmTokenIfLoggedIn()
        }
    }

    override fun onNewIntent(intent: Intent) {'''

    text = text.replace(anchor, on_resume_block, 1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print("✅ MainActivity.kt пропатчен")
    return True


def main():
    print("=" * 60)
    print("  FCM: регистрация токена в onResume")
    print("=" * 60)
    ok = patch_main_activity()
    print("\n" + "=" * 60)
    if ok:
        print("✅ Сборка: .\\gradlew assembleDebug")
        print("\nТест:")
        print("  1. Выйди из директора")
        print("  2. Войди под следопытом")
        print("  3. Проверь users/{teen_uid}/fcmTokens — должен появиться токен")
    else:
        print("❌ Не удалось")
    print("=" * 60)


if __name__ == "__main__":
    main()