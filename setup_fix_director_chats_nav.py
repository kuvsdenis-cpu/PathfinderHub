#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Фикс: кнопка «Чаты» у директора не открывает ChatsListScreen.
В PathfinderNavHost в вызов DirectorHomeScreen добавляем onNavigateToChats.
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


def patch_navhost() -> bool:
    print("\n🔧 Патчу PathfinderNavHost.kt...")
    f = PKG / "ui/navigation/PathfinderNavHost.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },\n                onNavigateToNotifications" in text \
       and text.count("onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) }") >= 2:
        print("⚠️  Уже пропатчен")
        return True

    # Ищем вызов DirectorHomeScreen — там есть onNavigateToContentManager
    anchor = "                onNavigateToContentManager = { navController.navigate(Routes.CONTENT_MANAGER) },"
    if anchor not in text:
        print("❌ Не найден якорь onNavigateToContentManager в вызове DirectorHomeScreen")
        return False

    new_line = anchor + "\n                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },"
    text = text.replace(anchor, new_line, 1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print("✅ PathfinderNavHost.kt пропатчен")
    return True


def main():
    print("=" * 60)
    print("  Fix: кнопка «Чаты» у директора")
    print("=" * 60)
    ok = patch_navhost()
    print("\n" + "=" * 60)
    if ok:
        print("✅ Сборка: .\\gradlew assembleDebug")
        print("\nТест:")
        print("  1. Войти под директором")
        print("  2. Нажать карточку «Чаты» → открывается ChatsListScreen")
    else:
        print("❌ Не удалось")
    print("=" * 60)


if __name__ == "__main__":
    main()