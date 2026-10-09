#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Восстанавливает потерянные поля в Routes.kt (групповые чаты).
Запуск:
    python fix_routes_recover.py
"""

import shutil
import sys
from pathlib import Path

ROOT = Path.cwd()
PATH = "app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt"

p = ROOT / PATH
if not p.exists():
    print("[ERROR] не найден: " + PATH); sys.exit(1)

text = p.read_text(encoding="utf-8")

# Защита от повторного запуска
if "customGroupChat" in text and "CREATE_GROUP_CHAT" in text and "CUSTOM_GROUP_CHAT" in text:
    print("[SKIP] Routes.kt уже содержит нужные поля, ничего не делаю.")
    sys.exit(0)

old = '''    const val DM_CHAT = "dm_chat/{dmId}/{otherUid}"
    fun dmChat(dmId: String, otherUid: String) = "dm_chat/$dmId/$otherUid"'''

new = '''    const val DM_CHAT = "dm_chat/{dmId}/{otherUid}"
    const val CREATE_GROUP_CHAT = "create_group_chat"
    const val CUSTOM_GROUP_CHAT = "custom_group_chat/{groupId}"
    fun customGroupChat(groupId: String) = "custom_group_chat/$groupId"
    fun dmChat(dmId: String, otherUid: String) = "dm_chat/$dmId/$otherUid"'''

if old not in text:
    print("[ERROR] Не нашёл ожидаемый блок с DM_CHAT. Возможно, файл уже другой.")
    print("Пришли текущий Routes.kt — восстановим вручную.")
    sys.exit(1)

# Безопасная копия
safe_bak = p.with_suffix(".kt.before_recovery.bak")
if not safe_bak.exists():
    shutil.copy2(p, safe_bak)
    print("[BAK]  " + safe_bak.name)

text = text.replace(old, new, 1)
p.write_text(text, encoding="utf-8", newline="\n")
print("[OK]   " + PATH)
print()
print("Готово. Теперь:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")