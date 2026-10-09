#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub - Share cards extra (I.2) - 08.10.2026

Добавляет Share на:
  - EventDetailScreen.kt (TopAppBar)
  - ChallengeDetailScreen.kt (кнопка под прогрессом)
  - MyTasksScreen.kt (карточки задач + карточки домашки)
  - HomeworkTab.kt (карточка домашки)

Использует ShareCardGenerator / ShareCardHelper из fix_share.py.

Запуск:
    python fix_share_extra.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m):
    print("[ERROR] " + m); sys.exit(1)


def patch(path, repls, skip_if_marker=None):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

    if skip_if_marker and skip_if_marker in text:
        print("[SKIP] " + path + " (уже пропатчен)")
        return

    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)

    for i, (o, n) in enumerate(repls, 1):
        if o not in text:
            die(path + ": шаблон #" + str(i) + ":\n" + o[:400])
        text = text.replace(o, n, 1)
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. EventDetailScreen.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/events/EventDetailScreen.kt", [
    # импорты
    ("import androidx.compose.material.icons.automirrored.filled.ArrowBack",
     "import androidx.compose.material.icons.automirrored.filled.ArrowBack\n"
     "import androidx.compose.material.icons.filled.Share"),

    ("import androidx.compose.runtime.remember",
     "import androidx.compose.runtime.remember\n"
     "import androidx.compose.runtime.rememberCoroutineScope\n"
     "import androidx.compose.ui.platform.LocalContext\n"
     "import com.pathfinder.hub.ui.share.ShareCardGenerator\n"
     "import com.pathfinder.hub.ui.share.ShareCardHelper\n"
     "import kotlinx.coroutines.Dispatchers\n"
     "import kotlinx.coroutines.launch\n"
     "import kotlinx.coroutines.withContext"),

    # actions в TopAppBar
    ("""                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )""",
     """                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    val ctx = LocalContext.current
                    val scope = rememberCoroutineScope()
                    val event = state.event
                    if (event != null) {
                        IconButton(onClick = {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    ShareCardGenerator.generate(
                                        context = ctx,
                                        emoji = "\\uD83D\\uDCC5",
                                        title = event.title,
                                        subtitle = event.location.ifBlank { "Событие клуба" },
                                        userName = null,
                                        dateText = fmt.format(event.dateStart),
                                    )
                                }
                                ShareCardHelper.share(ctx, file, "Событие в Pathfinder Hub: " + event.title)
                            }
                        }) {
                            Icon(Icons.Default.Share, "Поделиться")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )"""),
], skip_if_marker="ShareCardGenerator.generate")


# ============================================================
# 2. ChallengeDetailScreen.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/challenges/ChallengeDetailScreen.kt", [
    # импорты
    ("import androidx.compose.material.icons.filled.EmojiEvents",
     "import androidx.compose.material.icons.filled.EmojiEvents\n"
     "import androidx.compose.material.icons.filled.Share"),

    ("import androidx.compose.ui.Modifier",
     "import androidx.compose.ui.Modifier\n"
     "import androidx.compose.ui.platform.LocalContext\n"
     "import com.pathfinder.hub.ui.share.ShareCardGenerator\n"
     "import com.pathfinder.hub.ui.share.ShareCardHelper\n"
     "import kotlinx.coroutines.Dispatchers\n"
     "import kotlinx.coroutines.launch\n"
     "import kotlinx.coroutines.withContext"),

    # Share-кнопка под "Получить приз" / "Приз получен"
    ("""                        } else if (isRewardClaimed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Приз получен",
                                style = MaterialTheme.typography.bodySmall,
                                color = PathfinderBlue
                            )
                        }""",
     """                        } else if (isRewardClaimed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Приз получен",
                                style = MaterialTheme.typography.bodySmall,
                                color = PathfinderBlue
                            )
                        }

                        // ---------- Поделиться ----------
                        val ctx = LocalContext.current
                        val scope = rememberCoroutineScope()
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        ShareCardGenerator.generate(
                                            context = ctx,
                                            emoji = "\\uD83C\\uDFC6",
                                            title = challenge.title,
                                            subtitle = progress.progress.toString() + " / " +
                                                    challenge.goal + " " + challenge.unit,
                                            userName = null,
                                        )
                                    }
                                    ShareCardHelper.share(ctx, file, "Мой челлендж в Pathfinder Hub")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Поделиться")
                        }"""),
], skip_if_marker="ShareCardGenerator.generate")


# ============================================================
# 3. MyTasksScreen.kt — Share на TaskCard
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/tasks/MyTasksScreen.kt", [
    # импорты
    ("import androidx.compose.material.icons.filled.Add",
     "import androidx.compose.material.icons.filled.Add\n"
     "import androidx.compose.material.icons.filled.Share"),

    ("import androidx.compose.ui.Alignment",
     "import androidx.compose.ui.Alignment\n"
     "import androidx.compose.ui.platform.LocalContext\n"
     "import com.pathfinder.hub.ui.share.ShareCardGenerator\n"
     "import com.pathfinder.hub.ui.share.ShareCardHelper\n"
     "import kotlinx.coroutines.Dispatchers\n"
     "import kotlinx.coroutines.launch\n"
     "import kotlinx.coroutines.withContext"),

    # Share на TaskCard
    ("""            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.status == "assigned") {
                    Button(onClick = { vm.acceptTask(task.id) }) { Text("Принять") }
                }
                if (task.status == "accepted" || task.status == "in_progress") {
                    Button(onClick = { vm.completeTask(task.id) }) { Text("Выполнено") }
                }
            }""",
     """            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.status == "assigned") {
                    Button(onClick = { vm.acceptTask(task.id) }) { Text("Принять") }
                }
                if (task.status == "accepted" || task.status == "in_progress") {
                    Button(onClick = { vm.completeTask(task.id) }) { Text("Выполнено") }
                }
                Spacer(Modifier.weight(1f))
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                IconButton(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ShareCardGenerator.generate(
                                context = ctx,
                                emoji = "\\u2705",
                                title = task.title,
                                subtitle = "Статус: " + task.status,
                                userName = null,
                            )
                        }
                        ShareCardHelper.share(ctx, file, "Задача в Pathfinder Hub: " + task.title)
                    }
                }) {
                    Icon(Icons.Default.Share, "Поделиться", tint = PathfinderBlue)
                }
            }"""),
], skip_if_marker="ShareCardGenerator.generate")


# ============================================================
# 4. HomeworkTab.kt — Share на HomeworkCard
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/homework/HomeworkTab.kt", [
    # импорты
    ("import androidx.compose.material.icons.filled.CheckCircle",
     "import androidx.compose.material.icons.filled.CheckCircle\n"
     "import androidx.compose.material.icons.filled.Share\n"
     "import androidx.compose.material3.IconButton"),

    ("import androidx.compose.ui.Alignment",
     "import androidx.compose.ui.Alignment\n"
     "import androidx.compose.ui.platform.LocalContext\n"
     "import androidx.compose.runtime.rememberCoroutineScope\n"
     "import com.pathfinder.hub.ui.share.ShareCardGenerator\n"
     "import com.pathfinder.hub.ui.share.ShareCardHelper\n"
     "import kotlinx.coroutines.Dispatchers\n"
     "import kotlinx.coroutines.launch\n"
     "import kotlinx.coroutines.withContext"),

    # Share на HomeworkCard — в верхней строке заголовка
    ("""            Text(
                h.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )""",
     """            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                IconButton(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ShareCardGenerator.generate(
                                context = ctx,
                                emoji = "\\uD83D\\uDCDA",
                                title = h.title,
                                subtitle = "Срок: " + fmt.format(h.dueDate),
                                userName = null,
                            )
                        }
                        ShareCardHelper.share(ctx, file, "Домашка в Pathfinder Hub: " + h.title)
                    }
                }) {
                    Icon(Icons.Default.Share, "Поделиться", tint = PathfinderBlue)
                }
            }"""),
], skip_if_marker="ShareCardGenerator.generate")


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Проверка:")
print("  1. Событие → иконка Share в TopAppBar справа")
print("  2. Челлендж (детали) → кнопка 'Поделиться' под прогрессом")
print("  3. Мои задачи → вкладка 'Задачи' → иконка Share на карточке")
print("  4. Мои задачи → вкладка 'Домашка' → иконка Share на карточке")
print()
print("Откат: рядом с каждым файлом .bak.<timestamp>")