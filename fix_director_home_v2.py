#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fix DirectorHomeScreen: coroutine scope + alpha import."""
import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")
PATH = "app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeScreen.kt"

p = ROOT / PATH
if not p.exists():
    print("[ERROR] не найден: " + PATH); sys.exit(1)

raw = p.read_bytes()
crlf = b"\r\n" in raw
text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

changed = False

# 1) добавить импорты rememberCoroutineScope, launch, alpha
if "import androidx.compose.runtime.rememberCoroutineScope" not in text:
    old = "import androidx.compose.runtime.remember\n"
    new = "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\n"
    if old not in text:
        print("[ERROR] не нашёл 'import androidx.compose.runtime.remember'"); sys.exit(1)
    text = text.replace(old, new, 1)
    changed = True

if "import kotlinx.coroutines.launch" not in text:
    # Ищем последний import из androidx.hilt...
    anchor = "import androidx.hilt.navigation.compose.hiltViewModel\n"
    if anchor not in text:
        print("[ERROR] не нашёл import hiltViewModel"); sys.exit(1)
    text = text.replace(anchor, anchor + "import kotlinx.coroutines.launch\n", 1)
    changed = True

if "import androidx.compose.ui.draw.alpha" not in text:
    anchor2 = "import androidx.compose.ui.Modifier\n"
    if anchor2 not in text:
        print("[ERROR] не нашёл import Modifier"); sys.exit(1)
    text = text.replace(anchor2, "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.alpha\n", 1)
    changed = True

# 2) заменить MainScope().launch на scope из rememberCoroutineScope
if "kotlinx.coroutines.MainScope().launch" in text:
    # Добавляем val scope в тело функции
    old_guard = """    /**
     * Обёртка: если права нет — snackbar «Нет доступа», иначе — навигация.
     */
    fun guard(perm: String, onAllow: () -> Unit) {
        if (PermissionChecker.hasPerm(state.currentUser, perm)) {
            onAllow()
        } else {
            // Показываем сообщение через coroutine
            kotlinx.coroutines.MainScope().launch {
                snackbarHostState.showSnackbar("Нет доступа. Обратитесь к владельцу клуба.")
            }
        }
    }"""
    new_guard = """    /**
     * Обёртка: если права нет — snackbar «Нет доступа», иначе — навигация.
     */
    val scope = rememberCoroutineScope()
    fun guard(perm: String, onAllow: () -> Unit) {
        if (PermissionChecker.hasPerm(state.currentUser, perm)) {
            onAllow()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Нет доступа. Обратитесь к владельцу клуба.")
            }
        }
    }"""
    if old_guard not in text:
        # Пробуем без комментария
        print("[WARN] не нашёл точный блок guard — ищу по кускам")
        if "kotlinx.coroutines.MainScope().launch" in text:
            text = text.replace("kotlinx.coroutines.MainScope().launch", "rememberCoroutineScope().launch", 1)
            changed = True
    else:
        text = text.replace(old_guard, new_guard, 1)
        changed = True

# 3) заменить Modifier.alphaCompat(0.45f) на Modifier.alpha(0.45f)
if "Modifier.alphaCompat(0.45f)" in text:
    text = text.replace("Modifier.alphaCompat(0.45f)", "Modifier.alpha(0.45f)")
    changed = True

# 4) удалить функцию alphaCompat в конце файла
old_fn = """
/**
 * Локальная альтернатива Modifier.alpha — не требуем импорт
 * androidx.compose.ui.draw.alpha (чтобы не менять build.gradle).
 */
private fun Modifier.alphaCompat(value: Float): Modifier = this.then(
    androidx.compose.ui.draw.alpha(value)
)
"""
if old_fn in text:
    text = text.replace(old_fn, "")
    changed = True

if not changed:
    print("[SKIP] ничего менять не нужно — возможно уже пропатчено")
    sys.exit(0)

bak = p.with_suffix(p.suffix + ".bak." + STAMP)
shutil.copy2(p, bak)
print("[BAK]  " + bak.name)

p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
print("[OK]   " + PATH)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")