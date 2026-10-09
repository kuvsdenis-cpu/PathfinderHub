#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fix smart-cast error in InstructorHomeScreen / SecretaryHomeScreen."""
import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")

TARGETS = [
    "app/src/main/java/com/pathfinder/hub/ui/screens/home/instructor/InstructorHomeScreen.kt",
    "app/src/main/java/com/pathfinder/hub/ui/screens/home/secretary/SecretaryHomeScreen.kt",
]

OLD = """                    if (!state.clubLocation.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(state.clubLocation, style = MaterialTheme.typography.bodyMedium)
                    }"""

NEW = """                    val loc = state.clubLocation
                    if (!loc.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(loc, style = MaterialTheme.typography.bodyMedium)
                    }"""

for path in TARGETS:
    p = ROOT / path
    if not p.exists():
        print("[SKIP] " + path + " (не найден)")
        continue
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

    if "val loc = state.clubLocation" in text:
        print("[SKIP] " + path + " (уже пропатчен)")
        continue
    if OLD not in text:
        print("[WARN] " + path + " — шаблон не найден, пропускаю")
        continue

    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)

    text = text.replace(OLD, NEW, 1)
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)

print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")