#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fix missing TopAppBarDefaults import."""
import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")
PATH = "app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorInvitesScreen.kt"

p = ROOT / PATH
if not p.exists():
    print("[ERROR] не найден: " + PATH); sys.exit(1)

raw = p.read_bytes()
crlf = b"\r\n" in raw
text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

if "import androidx.compose.material3.TopAppBarDefaults" in text:
    print("[SKIP] TopAppBarDefaults уже импортирован")
    sys.exit(0)

old = "import androidx.compose.material3.TopAppBar\n"
new = "import androidx.compose.material3.TopAppBar\nimport androidx.compose.material3.TopAppBarDefaults\n"

if old not in text:
    print("[ERROR] не нашёл 'import androidx.compose.material3.TopAppBar'")
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