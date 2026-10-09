#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fix ParentRepository.confirmPayment signature."""
import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")
PATH = "app/src/main/java/com/pathfinder/hub/data/repository/ParentRepository.kt"

p = ROOT / PATH
if not p.exists():
    print("[ERROR] не найден: " + PATH); sys.exit(1)

raw = p.read_bytes()
crlf = b"\r\n" in raw
text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

old = """    suspend fun confirmPayment(id: String, status: String, by: String) =
        dao.confirmPayment(id, status, by, System.currentTimeMillis())"""

new = """    suspend fun confirmPayment(
        id: String,
        status: String,
        by: String,
        at: Long = System.currentTimeMillis(),
    ) = dao.confirmPayment(id, status, by, at)"""

if new in text:
    print("[SKIP] ParentRepository уже пропатчен")
    sys.exit(0)

if old not in text:
    print("[ERROR] не нашёл 'confirmPayment' в ParentRepository")
    print("Пришли текущий ParentRepository.kt — поправлю вручную")
    sys.exit(1)

bak = p.with_suffix(p.suffix + ".bak." + STAMP)
shutil.copy2(p, bak)
print("[BAK]  " + bak.name)

text = text.replace(old, new, 1)
p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
print("[OK]   " + PATH)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")