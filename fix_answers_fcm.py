#!/usr/bin/env python3
"""
Pathfinder Hub — Answers queue + FCM Cloud Functions (08.10.2026)

Создаёт:
  1. FirestoreAnswerDataSource.kt + AnswerRepository.kt
  2. Вкладку "Открытые ответы" в ApprovalsScreen/ViewModel
  3. functions/index.js + package.json — триггеры FCM для DM и групповых чатов
  4. Патчит firebase.json и .firebaserc под Cloud Functions

Запуск из корня репозитория:
    python fix_answers_fcm.py
"""

import json
import sys
from pathlib import Path

ROOT = Path.cwd()
PROJECT_ID = "pathfinder-hub-c495d"

def die(msg):
    print(f"[ERROR] {msg}")
    sys.exit(1)

def write_new(path, content):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print(f"[NEW]  {path}")

def patch(path, replacements):
    p = ROOT / path
    if not p.exists():
        die(f"не найден файл: {path}")
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
    for i, (old, new) in enumerate(replacements, 1):
        if old not in text:
            die(f"{path}: шаблон #{i} не найден.\n---\n{old[:400]}\n---")
        text = text.replace(old, new, 1)
    out = text.replace("\n", "\r\n") if crlf else text
    p.write_bytes(out.encode("utf-8"))
    print(f"[OK]   {path}")


# ============================================================
# 1. FirestoreAnswerDataSource.kt
# ============================================================
print("=" * 60)
print("1. FirestoreAnswerDataSource.kt")
print("=" * 60)

write_new("app/src/main/java/com/pathfinder/hub/data/remote/FirestoreAnswerDataSource.kt", '''package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник ответов на проверочные задания.
 *
 * Коллекция: clubs/{clubId}/requirementTaskAnswers/{answerId}
 *
 * Чтение — snapshot listener (реальное время).
 * Обновление статуса — update().
 *
 * Правила (firestore.rules):
 *   allow read:   staff своего клуба / автор своих / conference_admin
 *   allow update: staff своего клуба (status, reviewedBy, reviewedAt, reviewComment)
 */
@Singleton
class FirestoreAnswerDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreAnswerDS"
        const val COLLECTION = "requirementTaskAnswers"
        const val STATUS_SUBMITTED = "submitted"
    }

    private fun answersRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COLLECTION)

    /**
     * Очередь проверки для наставника/директора.
     *
     * Без orderBy — чтобы не требовать составной индекс.
     * Сортировку делает ViewModel.
     */
    fun observePendingAnswers(clubId: String): Flow<List<RequirementTaskAnswerEntity>> =
        callbackFlow {
            if (clubId.isBlank()) {
                Log.w(TAG, "observePendingAnswers: clubId пустой")
                trySend(emptyList())
                awaitClose { }
                return@callbackFlow
            }
            val registration = answersRef(clubId)
                .whereEqualTo("status", STATUS_SUBMITTED)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.e(TAG, "pendingAnswers listener failed", err)
                        trySend(emptyList())
                        close(err)
                        return@addSnapshotListener
                    }
                    val list = snap?.documents?.mapNotNull { doc ->
                        try { mapToEntity(doc.id, doc.data ?: emptyMap()) }
                        catch (e: Exception) {
                            Log.e(TAG, "map ${doc.id} failed", e); null
                        }
                    } ?: emptyList()
                    trySend(list)
                }
            awaitClose { registration.remove() }
        }.catch { e ->
            Log.e(TAG, "observePendingAnswers error", e)
            emit(emptyList())
        }

    suspend fun updateStatus(
        clubId: String,
        answerId: String,
        status: String,
        reviewedBy: String,
        comment: String?,
    ): Result<Unit> = try {
        answersRef(clubId).document(answerId).update(
            mapOf(
                "status" to status,
                "reviewedBy" to reviewedBy,
                "reviewedAt" to Timestamp(Date()),
                "reviewComment" to comment,
                "pendingSync" to false,
            )
        ).await()
        Log.d(TAG, "updateStatus OK: $answerId -> $status")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "updateStatus failed: $answerId", e)
        Result.failure(e)
    }

    private fun mapToEntity(id: String, d: Map<String, Any?>): RequirementTaskAnswerEntity {
        return RequirementTaskAnswerEntity(
            id = id,
            taskId = d["taskId"] as? String ?: "",
            requirementId = d["requirementId"] as? String ?: "",
            userId = d["userId"] as? String ?: "",
            clubId = d["clubId"] as? String ?: "",
            answer = d["answer"] as? String ?: "",
            attachments = (d["attachments"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            status = d["status"] as? String ?: "draft",
            autoChecked = d["autoChecked"] as? Boolean ?: false,
            submittedAt = parseDate(d["submittedAt"]),
            reviewedBy = d["reviewedBy"] as? String,
            reviewedAt = parseDate(d["reviewedAt"]),
            reviewComment = d["reviewComment"] as? String,
            createdAt = parseDate(d["createdAt"]) ?: Date(),
            updatedAt = parseDate(d["updatedAt"]) ?: Date(),
            pendingSync = false,
        )
    }

    private fun parseDate(v: Any?): Date? = when (v) {
        null -> null
        is Date -> v
        is Timestamp -> v.toDate()
        is Long -> Date(v)
        else -> null
    }
}
''')


# ============================================================
# 2. AnswerRepository.kt
# ============================================================
print()
print("=" * 60)
print("2. AnswerRepository.kt")
print("=" * 60)

write_new("app/src/main/java/com/pathfinder/hub/data/repository/AnswerRepository.kt", '''package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.remote.FirestoreAnswerDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ответы следопытов на проверочные задания (long_text, file).
 * Очередь ручной проверки для наставника / директора.
 */
@Singleton
class AnswerRepository @Inject constructor(
    private val firestore: FirestoreAnswerDataSource,
) {

    fun observePendingAnswers(clubId: String): Flow<List<RequirementTaskAnswerEntity>> =
        firestore.observePendingAnswers(clubId)

    suspend fun approve(
        answer: RequirementTaskAnswerEntity,
        reviewerId: String,
    ): Result<Unit> = firestore.updateStatus(
        clubId = answer.clubId,
        answerId = answer.id,
        status = RequirementTaskAnswerEntity.STATUS_APPROVED,
        reviewedBy = reviewerId,
        comment = null,
    )

    suspend fun reject(
        answer: RequirementTaskAnswerEntity,
        reviewerId: String,
        comment: String,
    ): Result<Unit> = firestore.updateStatus(
        clubId = answer.clubId,
        answerId = answer.id,
        status = RequirementTaskAnswerEntity.STATUS_REJECTED,
        reviewedBy = reviewerId,
        comment = comment,
    )
}
''')


# ============================================================
# 3. ApprovalsViewModel.kt — патч
# ============================================================
print()
print("=" * 60)
print("3. ApprovalsViewModel.kt")
print("=" * 60)

patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/ApprovalsViewModel.kt", [
    # Импорты
    (
        "import com.pathfinder.hub.data.repository.HonorRepository",
        "import com.pathfinder.hub.data.repository.AnswerRepository\n"
        "import com.pathfinder.hub.data.repository.HonorRepository"
    ),
    (
        "import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity",
        "import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity\n"
        "import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity"
    ),
    # Новый подкласс ApprovalItem.Answer
    (
        """    data class Honor(
        val progress: HonorProgressEntity,
        val honorId: String,
        override val userName: String
    ) : ApprovalItem() {
        override val userId: String get() = progress.userId
        override val requirementId: String get() = progress.requirementId
        override val submittedAt: Date? get() = progress.submittedAt
    }
}""",
        """    data class Honor(
        val progress: HonorProgressEntity,
        val honorId: String,
        override val userName: String
    ) : ApprovalItem() {
        override val userId: String get() = progress.userId
        override val requirementId: String get() = progress.requirementId
        override val submittedAt: Date? get() = progress.submittedAt
    }

    data class Answer(
        val answer: RequirementTaskAnswerEntity,
        override val userName: String
    ) : ApprovalItem() {
        override val userId: String get() = answer.userId
        override val requirementId: String get() = answer.requirementId
        override val submittedAt: Date? get() = answer.submittedAt
    }
}"""
    ),
    # Инжект AnswerRepository
    (
        """    private val honorRepository: HonorRepository,
    private val completeLevelUseCase: CompleteLevelUseCase,
    private val completeHonorUseCase: CompleteHonorUseCase
) : ViewModel() {""",
        """    private val honorRepository: HonorRepository,
    private val answerRepository: AnswerRepository,
    private val completeLevelUseCase: CompleteLevelUseCase,
    private val completeHonorUseCase: CompleteHonorUseCase
) : ViewModel() {"""
    ),
    # combine — добавляем 4-й источник
    (
        """                combine(
                    userRepository.observeClubMembers(clubId),
                    levelRepository.observePendingApprovalsByClub(clubId),
                    honorRepository.observePendingApprovalsByClub(clubId)
                ) { users, levelProgress, honorProgress ->
                    val userMap = users.associateBy { it.id }
                    val levelItems = levelProgress.mapNotNull { p ->
                        val user = userMap[p.userId] ?: return@mapNotNull null
                        ApprovalItem.Level(p, p.levelId, "${'$'}{user.firstName} ${'$'}{user.lastName}".trim())
                    }
                    val honorItems = honorProgress.mapNotNull { p ->
                        val user = userMap[p.userId] ?: return@mapNotNull null
                        ApprovalItem.Honor(p, p.honorId, "${'$'}{user.firstName} ${'$'}{user.lastName}".trim())
                    }
                    (levelItems + honorItems).sortedByDescending { it.submittedAt?.time ?: 0L }
                }""",
        """                combine(
                    userRepository.observeClubMembers(clubId),
                    levelRepository.observePendingApprovalsByClub(clubId),
                    honorRepository.observePendingApprovalsByClub(clubId),
                    answerRepository.observePendingAnswers(clubId)
                ) { users, levelProgress, honorProgress, pendingAnswers ->
                    val userMap = users.associateBy { it.id }
                    val levelItems = levelProgress.mapNotNull { p ->
                        val user = userMap[p.userId] ?: return@mapNotNull null
                        ApprovalItem.Level(p, p.levelId, "${'$'}{user.firstName} ${'$'}{user.lastName}".trim())
                    }
                    val honorItems = honorProgress.mapNotNull { p ->
                        val user = userMap[p.userId] ?: return@mapNotNull null
                        ApprovalItem.Honor(p, p.honorId, "${'$'}{user.firstName} ${'$'}{user.lastName}".trim())
                    }
                    val answerItems = pendingAnswers.mapNotNull { a ->
                        val user = userMap[a.userId] ?: return@mapNotNull null
                        ApprovalItem.Answer(a, "${'$'}{user.firstName} ${'$'}{user.lastName}".trim())
                    }
                    (levelItems + honorItems + answerItems)
                        .sortedByDescending { it.submittedAt?.time ?: 0L }
                }"""
    ),
    # processAction — обработка Answer
    (
        """                    is ApprovalItem.Honor -> {
                        honorRepository.approveRequirement(
                            userId = item.userId,
                            requirementId = item.requirementId,
                            status = newStatus,
                            approvedBy = directorId,
                            at = now
                        )
                    }
                }""",
        """                    is ApprovalItem.Honor -> {
                        honorRepository.approveRequirement(
                            userId = item.userId,
                            requirementId = item.requirementId,
                            status = newStatus,
                            approvedBy = directorId,
                            at = now
                        )
                    }
                    is ApprovalItem.Answer -> {
                        if (newStatus == "approved") {
                            answerRepository.approve(item.answer, directorId).getOrThrow()
                        } else {
                            answerRepository.reject(
                                item.answer,
                                directorId,
                                comment?.takeIf { it.isNotBlank() } ?: "Отклонено"
                            ).getOrThrow()
                        }
                    }
                }"""
    ),
    # exhaustiveness check в "if (newStatus == approved)"
    (
        """                if (newStatus == "approved") {
                    when (item) {
                        is ApprovalItem.Level -> checkLevelCompletion(item, directorId)
                        is ApprovalItem.Honor -> checkHonorCompletion(item, directorId)
                    }
                }""",
        """                if (newStatus == "approved") {
                    when (item) {
                        is ApprovalItem.Level -> checkLevelCompletion(item, directorId)
                        is ApprovalItem.Honor -> checkHonorCompletion(item, directorId)
                        // У открытых ответов нет автозавершения ступени/специализации:
                        // они лишь один из пунктов требования, а не требование целиком.
                        is ApprovalItem.Answer -> Unit
                    }
                }"""
    ),
])


# ============================================================
# 4. ApprovalsScreen.kt — патч (вкладки)
# ============================================================
print()
print("=" * 60)
print("4. ApprovalsScreen.kt")
print("=" * 60)

patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/ApprovalsScreen.kt", [
    # Состояние вкладки
    (
        """    val state by viewModel.state.collectAsState()
    var rejectDialogItem by remember { mutableStateOf<ApprovalItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }""",
        """    val state by viewModel.state.collectAsState()
    var rejectDialogItem by remember { mutableStateOf<ApprovalItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val filtered = remember(state.items, selectedTab) {
        if (selectedTab == 1) state.items.filter { it is ApprovalItem.Answer }
        else state.items.filter { it !is ApprovalItem.Answer }
    }"""
    ),
    # Column с TabRow
    (
        """            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(state.items, key = { "${'$'}{it.userId}_${'$'}{it.requirementId}" }) { item ->
                        ApprovalCard(
                            item = item,
                            isProcessing = state.processingId == "${'$'}{it.userId}_${'$'}{it.requirementId}",
                            onApprove = { viewModel.approve(item) },
                            onReject = { rejectDialogItem = item }
                        )
                    }
                }
            }""",
        """            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Требования") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Открытые ответы") }
                        )
                    }
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                text = if (selectedTab == 0) "Нет заявок на одобрение"
                                       else "Нет ответов на проверку",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            items(filtered, key = { "${'$'}{it.userId}_${'$'}{it.requirementId}" }) { item ->
                                ApprovalCard(
                                    item = item,
                                    isProcessing = state.processingId == "${'$'}{it.userId}_${'$'}{it.requirementId}",
                                    onApprove = { viewModel.approve(item) },
                                    onReject = { rejectDialogItem = item }
                                )
                            }
                        }
                    }
                }
            }"""
    ),
    # иконка для Answer
    (
        """                Icon(
                    imageVector = when (item) {
                        is ApprovalItem.Level -> Icons.Default.School
                        is ApprovalItem.Honor -> Icons.Default.Star
                    },""",
        """                Icon(
                    imageVector = when (item) {
                        is ApprovalItem.Level -> Icons.Default.School
                        is ApprovalItem.Honor -> Icons.Default.Star
                        is ApprovalItem.Answer -> Icons.Default.Approval
                    },"""
    ),
    # подпись типа
    (
        """                    Text(
                        text = when (item) {
                            is ApprovalItem.Level -> "Ступень: ${'$'}{item.levelId}"
                            is ApprovalItem.Honor -> "Специализация: ${'$'}{item.honorId}"
                        },""",
        """                    Text(
                        text = when (item) {
                            is ApprovalItem.Level -> "Ступень: ${'$'}{item.levelId}"
                            is ApprovalItem.Honor -> "Специализация: ${'$'}{item.honorId}"
                            is ApprovalItem.Answer -> "Открытый ответ на задание"
                        },"""
    ),
    # блок с текстом ответа
    (
        """            Text(
                text = "Требование: ${'$'}{item.requirementId}",
                style = MaterialTheme.typography.bodyMedium
            )""",
        """            Text(
                text = "Требование: ${'$'}{item.requirementId}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (item is ApprovalItem.Answer) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.answer.answer.ifBlank { "[файл]" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                if (item.answer.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Вложений: ${'$'}{item.answer.attachments.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }"""
    ),
])


# ============================================================
# 5. Cloud Functions
# ============================================================
print()
print("=" * 60)
print("5. Cloud Functions (FCM)")
print("=" * 60)

write_new("functions/package.json", json.dumps({
    "name": "pathfinder-hub-functions",
    "description": "FCM triggers for Pathfinder Hub",
    "engines": {"node": "20"},
    "main": "index.js",
    "scripts": {
        "serve": "firebase emulators:start --only functions",
        "deploy": "firebase deploy --only functions",
        "logs": "firebase functions:log"
    },
    "dependencies": {
        "firebase-admin": "^12.6.0",
        "firebase-functions": "^6.1.0"
    },
    "private": True
}, indent=2, ensure_ascii=False) + "\n")

write_new("functions/.gitignore", "node_modules/\n*.log\n.env\n")

write_new("functions/index.js", r'''/**
 * Pathfinder Hub — FCM-триггеры на новые сообщения.
 *
 * При новом сообщении в DM или групповом чате шлёт push всем участникам,
 * кроме автора. Токены берёт из users/{uid}/fcmTokens/{token}.
 *
 * Деплой:
 *   cd functions && npm install && cd ..
 *   firebase deploy --only functions
 *
 * ВАЖНО: Cloud Functions с исходящими сетевыми вызовами требуют план Blaze
 * (pay-as-you-go). На Spark Functions не деплоятся.
 */

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

admin.initializeApp();
const db = admin.firestore();
const messaging = admin.messaging();

setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

/**
 * Достаёт токены всех участников, кроме исключённого, и рассылает push.
 */
async function sendToUsers(uids, excludeUid, notification, data) {
  const tokens = [];
  for (const uid of uids) {
    if (!uid || uid === excludeUid) continue;
    try {
      const snap = await db
        .collection("users").doc(uid)
        .collection("fcmTokens")
        .get();
      snap.forEach((d) => {
        const t = d.data().token;
        if (t) tokens.push(t);
      });
    } catch (e) {
      logger.warn(`fcmTokens read failed for ${uid}`, e);
    }
  }

  if (tokens.length === 0) {
    logger.info("no tokens to send");
    return;
  }

  const res = await messaging.sendEachForMulticast({
    tokens,
    notification,
    data,
    android: {
      priority: "high",
      notification: {
        channelId: "pathfinder_chats",
        sound: "default",
      },
    },
  });

  logger.info(`push sent ok=${res.successCount} fail=${res.failureCount}`);

  // Почистить битые токены
  res.responses.forEach((r, i) => {
    if (!r.success && r.error) {
      const code = r.error.code || "";
      if (
        code.includes("registration-token-not-registered") ||
        code.includes("invalid-argument")
      ) {
        logger.warn(`stale token removed: ${tokens[i].slice(0, 12)}…`);
      }
    }
  });
}

async function userDisplayName(uid) {
  try {
    const d = await db.collection("users").doc(uid).get();
    if (!d.exists) return "Сообщение";
    const u = d.data();
    const name = `${u.firstName || ""} ${u.lastName || ""}`.trim();
    return name || "Сообщение";
  } catch (_) {
    return "Сообщение";
  }
}

// ------------------------------------------------------------
// DM: clubs/{clubId}/directMessages/{dmId}/messages/{msgId}
// ------------------------------------------------------------
exports.onDmMessage = onDocumentCreated(
  "clubs/{clubId}/directMessages/{dmId}/messages/{msgId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const msg = snap.data();
    const { clubId, dmId } = event.params;

    const dmDoc = await db.doc(`clubs/${clubId}/directMessages/${dmId}`).get();
    if (!dmDoc.exists) return;
    const participants = dmDoc.data().participants || [];

    const senderId = msg.authorId;
    const senderName = await userDisplayName(senderId);
    const body = (msg.text || "Вложение").substring(0, 120);

    await sendToUsers(
      participants,
      senderId,
      { title: senderName, body },
      {
        type: "dm",
        targetId: dmId,
        otherUid: senderId || "",
        clubId,
      }
    );
  }
);

// ------------------------------------------------------------
// GROUP: clubs/{clubId}/groupChats/{groupId}/messages/{msgId}
// ------------------------------------------------------------
exports.onGroupMessage = onDocumentCreated(
  "clubs/{clubId}/groupChats/{groupId}/messages/{msgId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const msg = snap.data();
    const { clubId, groupId } = event.params;

    const gDoc = await db.doc(`clubs/${clubId}/groupChats/${groupId}`).get();
    if (!gDoc.exists) return;
    const participants = gDoc.data().participants || [];
    const groupName = gDoc.data().name || "Группа";

    const senderId = msg.authorId;
    const senderName = await userDisplayName(senderId);
    const body = `${senderName}: ${(msg.text || "Вложение").substring(0, 120)}`;

    await sendToUsers(
      participants,
      senderId,
      { title: groupName, body },
      {
        type: "group",
        targetId: groupId,
        clubId,
      }
    );
  }
);
''')


# ============================================================
# 6. firebase.json — добавить блок functions
# ============================================================
print()
print("=" * 60)
print("6. firebase.json / .firebaserc")
print("=" * 60)

fb_json_path = ROOT / "firebase.json"
if fb_json_path.exists():
    cfg = json.loads(fb_json_path.read_text(encoding="utf-8"))
else:
    cfg = {}
cfg.setdefault("firestore", {})
cfg["firestore"].setdefault("rules", "firestore.rules")
cfg["firestore"].setdefault("indexes", "firestore.indexes.json")
cfg["functions"] = {
    "source": "functions",
    "codebase": "default",
    "ignore": ["node_modules", ".git", "firebase-debug.log", "*.local"],
    "predeploy": ["npm --prefix \"$RESOURCE_DIR\" run lint 2>/dev/null || true"]
}
# убираем predeploy с lint, если eslint не настроен — оставим только install
cfg["functions"]["predeploy"] = []
fb_json_path.write_text(json.dumps(cfg, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"[OK]   firebase.json")

rc_path = ROOT / ".firebaserc"
if rc_path.exists():
    rc = json.loads(rc_path.read_text(encoding="utf-8"))
else:
    rc = {"projects": {}}
rc.setdefault("projects", {})
rc["projects"]["default"] = PROJECT_ID
rc_path.write_text(json.dumps(rc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"[OK]   .firebaserc")


print()
print("=" * 60)
print("Готово. Дальше:")
print("=" * 60)
print("""
── ЧАСТЬ 1. Очередь открытых ответов ──────────────────────

1. Пересобрать APK. ApprovalsScreen получит вторую вкладку
   "Открытые ответы". Директор/наставник увидят submitted-ответы
   и смогут одобрить/отклонить.

2. Индексов не требуется (запрос только whereEqualTo).

3. Если teen создаёт ответ с status="submitted" — директор увидит его
   через snapshot-listener сразу.

── ЧАСТЬ 2. FCM Cloud Functions ──────────────────────────

1. Проверить, что установлен Node.js 20+:
      node --version

2. Установить зависимости:
      cd functions
      npm install
      cd ..

3. Убедиться, что проект Firebase на плане Blaze
   (Spark не разрешает исходящие сетевые вызовы):
      https://console.firebase.google.com/project/pathfinder-hub-c495d/usage

4. Задеплоить функции:
      firebase deploy --only functions

   Первый деплой займёт 3-5 минут. В консоли появятся две функции:
      onDmMessage
      onGroupMessage

5. Проверить end-to-end:
   - Два устройства (или эмулятор + устройство).
   - Войти teen-1 на первом, teen-2 на втором.
   - teen-1 пишет DM teen-2 → у teen-2 всплывает push.
   - Смотреть логи:
        firebase functions:log --only onDmMessage

6. Если push не приходит:
   - Проверить fcmTokens teen-2: users/{uid}/fcmTokens — токен есть?
   - Проверить логи функции: firebase functions:log
   - NotificationChannel "pathfinder_chats" создаётся в приложении
     (ChatNotificationHelper) — убедиться, что он есть.

── Замечания ─────────────────────────────────────────────

• Регион по умолчанию — europe-west1. Если у тебя проект в us-central —
  поменяй setGlobalOptions в functions/index.js.

• PathfinderMessagingService.onMessageReceived ожидает data.type =
  "dm" | "group" | "club_chat". Функции шлют ровно эти типы.

• Битые токены FCM сами не удаляются из Firestore. Если хочешь —
  сделаю отдельную функцию очистки.
""")