#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub - Share cards (I) - 08.10.2026

- NEW: ShareCardGenerator.kt (Bitmap 1080x1080 -> PNG в cacheDir)
- NEW: ShareCardHelper.kt (ACTION_SEND через FileProvider)
- PATCH: DigitalUniformScreen.kt (TopAppBar Share)
- PATCH: AchievementsScreen.kt (Share на earned-ачивках)
- PATCH: MyCertificatesScreen.kt (Share на карточке сертификата)

Запуск:
    python fix_share.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m):
    print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        die(path + ": файл уже существует.")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


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
# 1. ShareCardGenerator.kt
# ============================================================
GEN = '''package com.pathfinder.hub.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Генератор PNG-карточек для шаринга. 1080x1080.
 * Фон — PathfinderBlue, белый текст, внизу шапка PATHFINDER HUB.
 *
 * Файл кладётся в cacheDir/share_cards/ (разрешено в file_paths.xml).
 */
object ShareCardGenerator {

    private const val SIZE = 1080
    private const val AUTHORITY = "com.pathfinder.hub.fileprovider"
    private const val BG = 0xFF1A3A6E.toInt() // PathfinderBlue
    private const val WHITE = Color.WHITE
    private const val WHITE_70 = 0xB3FFFFFF.toInt()
    private const val WHITE_50 = 0x80FFFFFF.toInt()

    fun generate(
        context: Context,
        emoji: String,
        title: String,
        subtitle: String,
        userName: String?,
        dateText: String? = null,
    ): File {
        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val file = File(dir, "share_${System.currentTimeMillis()}.png")

        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(BG)

        val centerX = SIZE / 2f

        // ---------- Верхний декор: тонкая полоса ----------
        val barPaint = Paint().apply { color = 0x33FFFFFF }
        canvas.drawRect(0f, 0f, SIZE.toFloat(), 8f, barPaint)

        // ---------- Emoji ----------
        if (emoji.isNotBlank()) {
            val emojiPaint = Paint().apply {
                isAntiAlias = true
                textSize = 200f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(emoji, centerX, 340f, emojiPaint)
        }

        // ---------- Заголовок ----------
        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 72f
            color = WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        drawWrappedText(canvas, title, centerX, 480f, titlePaint, SIZE - 160f, 80f)

        // ---------- Подзаголовок ----------
        val subPaint = Paint().apply {
            isAntiAlias = true
            textSize = 40f
            color = WHITE_70
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(subtitle, centerX, 620f, subPaint)

        // ---------- Имя ----------
        if (!userName.isNullOrBlank()) {
            val namePaint = Paint().apply {
                isAntiAlias = true
                textSize = 52f
                color = WHITE
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText(userName, centerX, 720f, namePaint)
        }

        // ---------- Дата ----------
        val dateStr = dateText
            ?: SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date())
        val datePaint = Paint().apply {
            isAntiAlias = true
            textSize = 34f
            color = WHITE_50
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(dateStr, centerX, 790f, datePaint)

        // ---------- Нижняя шапка ----------
        val footerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 42f
            color = WHITE
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.2f
            isFakeBoldText = true
        }
        canvas.drawText("PATHFINDER HUB", centerX, 980f, footerPaint)

        // ---------- Рамка ----------
        val framePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = WHITE_50
        }
        canvas.drawRect(40f, 40f, SIZE - 40f, SIZE - 40f, framePaint)

        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bmp.recycle()

        return file
    }

    /**
     * Разбивает длинный текст на строки, если не влезает.
     */
    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        cx: Float,
        startY: Float,
        paint: Paint,
        maxWidth: Float,
        lineHeight: Float,
    ) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val test = if (current.isEmpty()) w else current.toString() + " " + w
            if (paint.measureText(test) <= maxWidth) {
                if (current.isNotEmpty()) current.append(" ")
                current.append(w)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())

        var y = startY
        for (line in lines) {
            canvas.drawText(line, cx, y, paint)
            y += lineHeight
        }
    }

    fun authority(): String = AUTHORITY
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/share/ShareCardGenerator.kt", GEN)


# ============================================================
# 2. ShareCardHelper.kt
# ============================================================
HELPER = '''package com.pathfinder.hub.ui.share

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Хелпер для ACTION_SEND PNG-карточки через FileProvider.
 */
object ShareCardHelper {

    private const val TAG = "ShareCardHelper"

    fun share(context: Context, file: File, caption: String = "") {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                ShareCardGenerator.authority(),
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться"))
        } catch (e: Exception) {
            Log.e(TAG, "share failed", e)
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/share/ShareCardHelper.kt", HELPER)


# ============================================================
# 3. DigitalUniformScreen.kt — Share в TopAppBar
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/gamification/DigitalUniformScreen.kt", [
    # импорты
    ("import androidx.compose.runtime.Composable",
     "import android.content.Context\n"
     "import androidx.compose.runtime.Composable\n"
     "import androidx.compose.runtime.rememberCoroutineScope\n"
     "import androidx.compose.ui.platform.LocalContext\n"
     "import com.pathfinder.hub.ui.share.ShareCardGenerator\n"
     "import com.pathfinder.hub.ui.share.ShareCardHelper\n"
     "import kotlinx.coroutines.Dispatchers\n"
     "import kotlinx.coroutines.launch\n"
     "import kotlinx.coroutines.withContext"),

    # share в TopAppBar
    ("""                actions = {
                    IconButton(onClick = { /* TODO: Share */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться")
                    }
                },""",
     """                actions = {
                    val ctx = LocalContext.current
                    val scope = rememberCoroutineScope()
                    IconButton(onClick = {
                        val u = state.uniform ?: return@IconButton
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                ShareCardGenerator.generate(
                                    context = ctx,
                                    emoji = "\\uD83D\\uDC55",
                                    title = "Цифровая форма",
                                    subtitle = "Нашивок: " + u.earnedBadges.size +
                                            ", шевронов: " + u.earnedChevrons.size,
                                    userName = null,
                                )
                            }
                            ShareCardHelper.share(ctx, file, "Моя цифровая форма в Pathfinder Hub")
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться")
                    }
                },"""),
], skip_if_marker="ShareCardGenerator.generate")


# ============================================================
# 4. AchievementsScreen.kt — Share на earned
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/gamification/AchievementsScreen.kt", [
    # импорты
    ("import androidx.compose.runtime.collectAsState",
     "import androidx.compose.runtime.collectAsState\n"
     "import androidx.compose.runtime.rememberCoroutineScope\n"
     "import androidx.compose.material.icons.filled.Share\n"
     "import androidx.compose.ui.platform.LocalContext\n"
     "import com.pathfinder.hub.ui.share.ShareCardGenerator\n"
     "import com.pathfinder.hub.ui.share.ShareCardHelper\n"
     "import kotlinx.coroutines.Dispatchers\n"
     "import kotlinx.coroutines.launch\n"
     "import kotlinx.coroutines.withContext"),

    # сама карточка: Share вместо Star (только для earned)
    ("""            if (isEarned) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Получено",
                    tint = PathfinderBlue,
                    modifier = Modifier.size(24.dp)
                )
            } else {""",
     """            if (isEarned) {
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                IconButton(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ShareCardGenerator.generate(
                                context = ctx,
                                emoji = achievement.icon,
                                title = achievement.name,
                                subtitle = achievement.description,
                                userName = null,
                            )
                        }
                        ShareCardHelper.share(ctx, file, "Моё достижение в Pathfinder Hub: " + achievement.name)
                    }
                }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Поделиться",
                        tint = PathfinderBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {"""),
], skip_if_marker="ShareCardGenerator.generate")


# ============================================================
# 5. MyCertificatesScreen.kt — Share на карточке
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/certificates/MyCertificatesScreen.kt", [
    # импорт иконки Share
    ("import androidx.compose.material.icons.filled.Description",
     "import androidx.compose.material.icons.filled.Description\n"
     "import androidx.compose.material.icons.filled.Share\n"
     "import com.pathfinder.hub.ui.share.ShareCardGenerator\n"
     "import com.pathfinder.hub.ui.share.ShareCardHelper"),

    # кнопка Share рядом со «Скачать PDF»
    ("""                                Button(onClick = {
                                    scope.launch {
                                        val file = withContext(Dispatchers.IO) {
                                            CertificatePdfGenerator.generate(
                                                context = context,
                                                certificateId = item.certificateId,
                                                userName = state.userName,
                                                title = item.title,
                                                subject = item.name,
                                                issuedAt = item.issuedAt,
                                            )
                                        }
                                        openPdf(context, file)
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Description, null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text("Скачать PDF")
                                }""",
     """                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        scope.launch {
                                            val file = withContext(Dispatchers.IO) {
                                                CertificatePdfGenerator.generate(
                                                    context = context,
                                                    certificateId = item.certificateId,
                                                    userName = state.userName,
                                                    title = item.title,
                                                    subject = item.name,
                                                    issuedAt = item.issuedAt,
                                                )
                                            }
                                            openPdf(context, file)
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.Description, null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.size(6.dp))
                                        Text("Скачать PDF")
                                    }
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            val file = withContext(Dispatchers.IO) {
                                                ShareCardGenerator.generate(
                                                    context = context,
                                                    emoji = "\\uD83C\\uDFC6",
                                                    title = item.name,
                                                    subtitle = item.title,
                                                    userName = state.userName,
                                                    dateText = fmt.format(item.issuedAt),
                                                )
                                            }
                                            ShareCardHelper.share(context, file, "Мой сертификат в Pathfinder Hub")
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.Share, null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.size(6.dp))
                                        Text("Поделиться")
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
print("  1. Цифровая форма → иконка Share в TopAppBar справа")
print("     → открывается системный выбор 'Поделиться'")
print("  2. Достижения → на earned-ачивке иконка Share")
print("  3. Сертификаты → карточка → кнопка 'Поделиться' рядом с 'Скачать PDF'")
print()
print("Откат: рядом с каждым файлом .bak.<timestamp>")