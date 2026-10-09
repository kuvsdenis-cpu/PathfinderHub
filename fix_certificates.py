#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub - PDF Certificates (H) - 08.10.2026

- Экран "Мои сертификаты" для teen
- Генерация PDF через android.graphics.pdf.PdfDocument (A4 landscape)
- Кнопка входа: TeenHomeScreen, рядом с "Чаты"
- Открытие через существующий FileProvider

Запуск:
    python fix_certificates.py
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
        die(path + ": файл уже существует. Ничего не перезаписываю.")
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
# 1. CertificatePdfGenerator.kt
# ============================================================
PDF_GEN = '''package com.pathfinder.hub.ui.screens.certificates

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Генератор PDF-сертификата через встроенный android.graphics.pdf.PdfDocument.
 *
 * Формат: A4 landscape (842 x 595 pt), рамка, шапка, крупный титул,
 * ФИО получателя, название ступени/специализации, дата, подпись.
 *
 * Файл пишется в cacheDir/certs/cert_<id>.pdf — этот путь разрешён
 * в res/xml/file_paths.xml (cache-path).
 */
object CertificatePdfGenerator {

    private const val PAGE_W = 842
    private const val PAGE_H = 595
    private const val AUTHORITY = "com.pathfinder.hub.fileprovider"

    fun generate(
        context: Context,
        certificateId: String,
        userName: String,
        title: String,        // "Сертификат о прохождении ступени"
        subject: String,      // "Следопыт" / "Первая помощь"
        issuedAt: Date,
    ): File {
        val dir = File(context.cacheDir, "certs").apply { mkdirs() }
        val file = File(dir, "cert_$certificateId.pdf")

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // ---------- Paints ----------
        val accent = Color.parseColor("#1A3A6E")
        val headerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 24f
            color = accent
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            letterSpacing = 0.15f
        }
        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 52f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 20f
            color = Color.DKGRAY
            textAlign = Paint.Align.CENTER
        }
        val subjectPaint = Paint().apply {
            isAntiAlias = true
            textSize = 44f
            color = accent
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val namePaint = Paint().apply {
            isAntiAlias = true
            textSize = 30f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val smallPaint = Paint().apply {
            isAntiAlias = true
            textSize = 14f
            color = Color.GRAY
            textAlign = Paint.Align.CENTER
        }
        val framePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = accent
        }
        val innerFramePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = accent
        }

        // ---------- Рамки ----------
        canvas.drawRect(20f, 20f, PAGE_W - 20f, PAGE_H - 20f, framePaint)
        canvas.drawRect(30f, 30f, PAGE_W - 30f, PAGE_H - 30f, innerFramePaint)

        val cx = PAGE_W / 2f

        // ---------- Содержимое ----------
        canvas.drawText("PATHFINDER HUB", cx, 90f, headerPaint)
        canvas.drawText("СЕРТИФИКАТ", cx, 180f, titlePaint)
        canvas.drawText(title, cx, 220f, subtitlePaint)

        canvas.drawText("«" + subject + "»", cx, 300f, subjectPaint)

        canvas.drawText("Выдан", cx, 355f, smallPaint)
        canvas.drawText(userName, cx, 395f, namePaint)

        val fmt = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
        canvas.drawText("Дата выдачи: " + fmt.format(issuedAt), cx, 445f, smallPaint)

        canvas.drawText("Pathfinder Hub", cx, 525f, smallPaint)

        pdf.finishPage(page)

        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        return file
    }

    fun authority(): String = AUTHORITY
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/certificates/CertificatePdfGenerator.kt", PDF_GEN)


# ============================================================
# 2. MyCertificatesViewModel.kt
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.certificates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class CertificateItem(
    val certificateId: String,
    val title: String,
    val name: String,
    val issuedAt: Date,
    val kind: String,      // "level" | "honor"
    val targetId: String,
)

data class MyCertificatesUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val items: List<CertificateItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class MyCertificatesViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyCertificatesUiState())
    val state: StateFlow<MyCertificatesUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не авторизован") }
            return
        }
        load(uid)
    }

    private fun load(userId: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.getUser(userId)
                _state.update {
                    it.copy(
                        userName = listOfNotNull(user?.firstName, user?.lastName)
                            .joinToString(" ").trim().ifBlank { "Следопыт" }
                    )
                }

                // Ступени
                levelRepository.observeCompletions(userId).collect { levels ->
                    val levelItems = levels.map { lc ->
                        val lvl = levelRepository.getLevel(lc.levelId)
                        CertificateItem(
                            certificateId = "level_${'$'}{lc.levelId}",
                            title = "Сертификат о прохождении ступени",
                            name = lvl?.name ?: lc.levelId,
                            issuedAt = lc.completedAt,
                            kind = "level",
                            targetId = lc.levelId,
                        )
                    }
                    _state.update { s ->
                        s.copy(
                            items = (s.items.filter { it.kind != "level" } + levelItems)
                                .sortedByDescending { it.issuedAt },
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }

        viewModelScope.launch {
            try {
                honorRepository.observeCompletions(userId).collect { honors ->
                    val honorItems = honors.map { hc ->
                        val h = honorRepository.getHonor(hc.honorId)
                        CertificateItem(
                            certificateId = "honor_${'$'}{hc.honorId}",
                            title = "Сертификат о специализации",
                            name = h?.name ?: hc.honorId,
                            issuedAt = hc.completedAt,
                            kind = "honor",
                            targetId = hc.honorId,
                        )
                    }
                    _state.update { s ->
                        s.copy(
                            items = (s.items.filter { it.kind != "honor" } + honorItems)
                                .sortedByDescending { it.issuedAt },
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/certificates/MyCertificatesViewModel.kt", VM)


# ============================================================
# 3. MyCertificatesScreen.kt
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.screens.certificates

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCertificatesScreen(
    onBack: () -> Unit,
    viewModel: MyCertificatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = SimpleDateFormat("d MMM yyyy", Locale("ru"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои сертификаты") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Description, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Сертификатов пока нет",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Заверши ступень или специализацию — сертификат появится здесь",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.certificateId }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Выдан: " + fmt.format(item.issuedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
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
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openPdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            CertificatePdfGenerator.authority(),
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Открыть сертификат"))
    } catch (e: Exception) {
        android.util.Log.e("Certificates", "openPdf failed", e)
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/certificates/MyCertificatesScreen.kt", SCREEN)


# ============================================================
# 4. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    // === Reports ===
    const val REPORT_HISTORY = "report_history\"""",
     """    // === Certificates ===
    const val MY_CERTIFICATES = "my_certificates"

    // === Reports ===
    const val REPORT_HISTORY = "report_history\""""),
], skip_if_marker='const val MY_CERTIFICATES')


# ============================================================
# 5. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    # import
    ("import com.pathfinder.hub.ui.screens.challenges.ChallengeDetailScreen",
     "import com.pathfinder.hub.ui.screens.certificates.MyCertificatesScreen\n"
     "import com.pathfinder.hub.ui.screens.challenges.ChallengeDetailScreen"),

    # TeenHome — добавить onNavigateToCertificates
    ("""                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        }

        // === Teen - Levels ===""",
     """                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToCertificates = { navController.navigate(Routes.MY_CERTIFICATES) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        }

        composable(Routes.MY_CERTIFICATES) {
            MyCertificatesScreen(onBack = { navController.popBackStack() })
        }

        // === Teen - Levels ==="""),
], skip_if_marker="Routes.MY_CERTIFICATES")


# ============================================================
# 6. TeenHomeScreen.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/teen/TeenHomeScreen.kt", [
    # import Description
    ("import androidx.compose.material.icons.filled.CheckCircle\nimport androidx.compose.material.icons.filled.EmojiEvents",
     "import androidx.compose.material.icons.filled.CheckCircle\nimport androidx.compose.material.icons.filled.Description\nimport androidx.compose.material.icons.filled.EmojiEvents"),

    # параметр
    ("""    onNavigateToChats: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    viewModel: TeenHomeViewModel = hiltViewModel()""",
     """    onNavigateToChats: () -> Unit = {},
    onNavigateToCertificates: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    viewModel: TeenHomeViewModel = hiltViewModel()"""),

    # ряд Чаты + пусто → Чаты + Сертификаты
    ("""            // Кнопки разделов - Ряд 4 (Чат клуба)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionTile(
                    icon = Icons.Default.Chat,
                    label = "Чаты",
                    onClick = onNavigateToChats,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }""",
     """            // Кнопки разделов - Ряд 4 (Чаты + Сертификаты)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionTile(
                    icon = Icons.Default.Chat,
                    label = "Чаты",
                    onClick = onNavigateToChats,
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = Icons.Default.Description,
                    label = "Сертификаты",
                    onClick = onNavigateToCertificates,
                    modifier = Modifier.weight(1f)
                )
            }"""),
], skip_if_marker="onNavigateToCertificates")


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Проверка:")
print("  1. Teen → Домой → ряд 'Чаты' + 'Сертификаты'")
print("  2. Тап 'Сертификаты' → 'Мои сертификаты'")
print("  3. Если есть завершённые ступени/специализации — карточки")
print("  4. 'Скачать PDF' → открывается системный просмотрщик PDF")
print()
print("Откат: рядом с каждым изменённым файлом .bak.<timestamp>")