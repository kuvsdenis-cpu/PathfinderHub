#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Фикс DMChatViewModel: обновить имена методов ChatRepository.
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


def patch_dm_vm() -> bool:
    print("\n🔧 Патчу DMChatViewModel.kt...")
    f = PKG / "ui/chat/dm/DMChatViewModel.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "observeDmMessages" in text and "sendDmMessage" in text:
        print("⚠️  Уже пропатчен")
        return True

    # Заменяем оба вызова
    text = text.replace("chatRepo.observeMessages(", "chatRepo.observeDmMessages(")
    text = text.replace("chatRepo.sendMessage(", "chatRepo.sendDmMessage(")

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(text, encoding="utf-8")
    print("✅ DMChatViewModel.kt пропатчен")
    return True


def main():
    print("=" * 60)
    print("  Фикс DMChatViewModel (методы ChatRepository)")
    print("=" * 60)
    ok = patch_dm_vm()
    print("\n" + "=" * 60)
    if ok:
        print("✅ Сборка: .\\gradlew assembleDebug")
    else:
        print("❌ Не удалось.")
    print("=" * 60)


if __name__ == "__main__":
    main()