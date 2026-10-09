#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Certificates upload to Yandex - 08.10.2026
Запуск:
    python fix_certificates_upload.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        print("[SKIP] " + path + " (уже есть)")
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


def rewrite(path, content):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[REWRITE] " + path)


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
# 1. FirestoreCertificateDataSource.kt
# ============================================================
DS = '''package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.pathfinder.hub.data.local.entity.gamification.CertificateEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для сертификатов.
 * Коллекция: users/{userId}/certificates/{certId}
 */
@Singleton
class FirestoreCertificateDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreCertDS"
        const val COL = "certificates"
    }

    private fun certsRef(userId: String) =
        firestore.collection("users").document(userId).collection(COL)

    suspend fun upsert(c: CertificateEntity): Result<Unit> = try {
        certsRef(c.userId).document(c.id).set(map(c)).await()
        Log.d(TAG, "cert upsert OK: ${c.userId}/${c.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "cert upsert FAILED: ${c.id}", e)
        Result.failure(e)
    }

    private fun map(c: CertificateEntity): Map<String, Any?> = mapOf(
        "id" to c.id,
        "userId" to c.userId,
        "type" to c.type,
        "targetId" to c.targetId,
        "issuedAt" to Timestamp(c.issuedAt),
        "pdfUrl" to c.pdfUrl,
        "imageUrl" to c.imageUrl,
    )
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/remote/FirestoreCertificateDataSource.kt", DS)


# ============================================================
# 2. AchievementRepository.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/repository/AchievementRepository.kt", [
    # импорт
    ("import com.pathfinder.hub.data.local.dao.AchievementDao",
     "import com.pathfinder.hub.data.local.dao.AchievementDao\n"
     "import com.pathfinder.hub.data.remote.FirestoreCertificateDataSource"),

    # конструктор — добавить DS
    ("""class AchievementRepository @Inject constructor(private val dao: AchievementDao) {""",
     """class AchievementRepository @Inject constructor(
    private val dao: AchievementDao,
    private val certFirestore: FirestoreCertificateDataSource,
) {"""),

    # метод с синком
    ("""    suspend fun upsertCertificate(c: CertificateEntity) = dao.upsertCertificate(c)""",
     """    suspend fun upsertCertificate(c: CertificateEntity) = dao.upsertCertificate(c)

    /**
     * Сохраняет сертификат локально + в Firestore.
     * Используется после upload PDF в Yandex.
     */
    suspend fun upsertCertificateWithSync(c: CertificateEntity) {
        dao.upsertCertificate(c)
        certFirestore.upsert(c)
    }"""),
], skip_if_marker="upsertCertificateWithSync")


# ============================================================
# 3. RepositoryModule.kt — если AchievementRepository был @Inject, ок
# ============================================================
# НЕ патчим — AchievementRepository имеет @Inject constructor, Hilt соберёт сам,
# FirestoreCertificateDataSource тоже @Inject constructor().


# ============================================================
# 4. MyCertificatesViewModel.kt — REWRITE
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.certificates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.CertificateEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.StorageRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

data class CertificateItem(
    val certificateId: String,
    val title: String,
    val name: String,
    val issuedAt: Date,
    val kind: String,
    val targetId: String,
)

data class MyCertificatesUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val items: List<CertificateItem> = emptyList(),
    val uploaded: Map<String, CertificateEntity> = emptyMap(),
    val uploadingId: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class MyCertificatesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val achievementRepository: AchievementRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyCertificatesUiState())
    val state: StateFlow<MyCertificatesUiState> = _state.asStateFlow()

    private val userId: String? = sessionManager.getUserId()

    init {
        val uid = userId
        if (uid == null) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "Пользователь не авторизован")
            }
        } else {
            load(uid)
        }
    }

    private fun load(userId: String) {
        // Имя
        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            val name = listOfNotNull(user?.firstName, user?.lastName)
                .joinToString(" ").trim().ifBlank { "Следопыт" }
            _state.update { it.copy(userName = name) }
        }

        // Завершённые ступени
        viewModelScope.launch {
            try {
                levelRepository.observeCompletions(userId).collect { levels ->
                    val items = levels.map { lc ->
                        val lvl = levelRepository.getLevel(lc.levelId)
                        CertificateItem(
                            certificateId = "level_" + lc.levelId,
                            title = "Сертификат о прохождении ступени",
                            name = lvl?.name ?: lc.levelId,
                            issuedAt = lc.completedAt,
                            kind = "level",
                            targetId = lc.levelId,
                        )
                    }
                    _state.update { s ->
                        s.copy(
                            items = (s.items.filter { it.kind != "level" } + items)
                                .sortedByDescending { it.issuedAt },
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }

        // Завершённые специализации
        viewModelScope.launch {
            try {
                honorRepository.observeCompletions(userId).collect { honors ->
                    val items = honors.map { hc ->
                        val h = honorRepository.getHonor(hc.honorId)
                        CertificateItem(
                            certificateId = "honor_" + hc.honorId,
                            title = "Сертификат о специализации",
                            name = h?.name ?: hc.honorId,
                            issuedAt = hc.completedAt,
                            kind = "honor",
                            targetId = hc.honorId,
                        )
                    }
                    _state.update { s ->
                        s.copy(
                            items = (s.items.filter { it.kind != "honor" } + items)
                                .sortedByDescending { it.issuedAt },
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }

        // Уже загруженные в облако
        viewModelScope.launch {
            try {
                achievementRepository.observeCertificates(userId).collect { list ->
                    _state.update { s ->
                        s.copy(uploaded = list.associateBy { it.id })
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Генерирует PDF локально, грузит в Yandex Bucket, сохраняет URL.
     */
    fun uploadCertificate(item: CertificateItem) {
        val uid = userId ?: return
        _state.update { it.copy(uploadingId = item.certificateId, message = null) }

        viewModelScope.launch {
            try {
                // 1. Генерация PDF (IO)
                val file = withContext(Dispatchers.IO) {
                    CertificatePdfGenerator.generate(
                        context = context,
                        certificateId = item.certificateId,
                        userName = _state.value.userName,
                        title = item.title,
                        subject = item.name,
                        issuedAt = item.issuedAt,
                    )
                }

                // 2. Upload в Yandex
                val safeId = item.certificateId.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val objectKey = "certificates/$uid/${safeId}.pdf"

                val uploadResult = storageRepository.uploadFile(file, objectKey) { }

                uploadResult.fold(
                    onSuccess = { url ->
                        // 3. Сохранить локально + Firestore
                        val entity = CertificateEntity(
                            id = item.certificateId,
                            userId = uid,
                            type = item.kind,
                            targetId = item.targetId,
                            issuedAt = item.issuedAt,
                            pdfUrl = url,
                            imageUrl = "",
                        )
                        achievementRepository.upsertCertificateWithSync(entity)
                        file.delete()

                        _state.update {
                            it.copy(uploadingId = null, message = "Сертификат в облаке")
                        }
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(
                                uploadingId = null,
                                errorMessage = "Ошибка загрузки: " + (e.message ?: "?")
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        uploadingId = null,
                        errorMessage = e.message ?: "Ошибка обработки"
                    )
                }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(message = null, errorMessage = null) }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/certificates/MyCertificatesViewModel.kt", VM)


# ============================================================
# 5. MyCertificatesScreen.kt — REWRITE
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.screens.certificates

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.gamification.CertificateEntity
import com.pathfinder.hub.ui.share.ShareCardGenerator
import com.pathfinder.hub.ui.share.ShareCardHelper
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
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
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

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
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center
            ) { CircularProgressIndicator() }

            state.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Сертификатов пока нет", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Заверши ступень или специализацию - сертификат появится здесь",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.certificateId }) { item ->
                    val uploaded = state.uploaded[item.certificateId]
                    val isUploading = state.uploadingId == item.certificateId
                    CertificateCard(
                        item = item,
                        fmt = fmt,
                        uploaded = uploaded,
                        isUploading = isUploading,
                        onDownloadPdf = {
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
                        },
                        onShare = {
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
                                ShareCardHelper.share(
                                    context, file,
                                    "Мой сертификат в Pathfinder Hub"
                                )
                            }
                        },
                        onUpload = { viewModel.uploadCertificate(item) },
                        onCopyLink = {
                            uploaded?.pdfUrl?.let { url ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("cert_url", url))
                                Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenLink = {
                            uploaded?.pdfUrl?.let { url ->
                                try {
                                    val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(i)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Не удалось открыть", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CertificateCard(
    item: CertificateItem,
    fmt: SimpleDateFormat,
    uploaded: CertificateEntity?,
    isUploading: Boolean,
    onDownloadPdf: () -> Unit,
    onShare: () -> Unit,
    onUpload: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenLink: () -> Unit,
) {
    val isInCloud = !uploaded?.pdfUrl.isNullOrBlank()

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

            if (isInCloud) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = PathfinderGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "В облаке",
                        color = PathfinderGreen,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDownloadPdf,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF")
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, "Поделиться", tint = PathfinderBlue)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isInCloud) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onOpenLink,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Открыть ссылку")
                    }
                    OutlinedButton(
                        onClick = onCopyLink,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Копировать")
                    }
                }
            } else {
                Button(
                    onClick = onUpload,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Загрузка...")
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Загрузить в облако")
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
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/certificates/MyCertificatesScreen.kt", SCREEN)


# ============================================================
# 6. firestore.rules — добавить certificates subcollection
# ============================================================
patch("firestore.rules", [
    ("""      match /fcmTokens/{tokenId} {
        allow read, write: if isSignedIn() && uid() == userId;
      }""",
     """      match /fcmTokens/{tokenId} {
        allow read, write: if isSignedIn() && uid() == userId;
      }
      match /certificates/{certId} {
        allow read: if isSignedIn() && (
          uid() == userId
          || isConferenceAdmin()
          || (isClubStaff() && get(/databases/$(database)/documents/users/$(userId)).data.clubId == userClubId())
        );
        allow create, update: if isSignedIn() && uid() == userId;
        allow delete: if isConferenceAdmin();
      }"""),
], skip_if_marker="match /certificates/")


print()
print("=" * 60)
print("Готово.")
print("=" * 60)
print()
print("Дальше:")
print("  1. Сборка:")
print("       .\\gradlew assembleDebug")
print("       .\\gradlew installDebug")
print("  2. Задеплой правила:")
print("       firebase deploy --only firestore:rules")
print()
print("Проверка:")
print("  Teen → Сертификаты → карточка → 'Загрузить в облако'")
print("  → 'В облаке' + кнопки 'Открыть ссылку' / 'Копировать'")
print("  → Firestore: users/{uid}/certificates/{certId}.pdfUrl")
print("  → Yandex Bucket: certificates/{uid}/{certId}.pdf")
print()
print("Откат: .bak.<timestamp>")