#!/usr/bin/env python3
"""
Pathfinder Hub — fix-all (08.10.2026)

Чинит 3 связанных бага:
  1. PERMISSION_DENIED при "Директор → Написать"
     — подписка на directMessages/groupChats/comments падает, если документа ещё нет.
  2. ModerationScreen не видит флаги с других устройств
     — читал из Room, а не из Firestore.
  3. RoleHomeScreen: ModerationScreenViewModel должен сам найти clubId.

Запуск из корня репозитория:
    python3 fix_all.py
"""

import sys
from pathlib import Path

ROOT = Path.cwd()

def die(msg):
    print(f"[ERROR] {msg}")
    sys.exit(1)

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
        if text.count(old) > 1:
            print(f"[WARN] {path}: шаблон #{i} встречается {text.count(old)} раз, заменяю первый")
        text = text.replace(old, new, 1)

    out = text.replace("\n", "\r\n") if crlf else text
    p.write_bytes(out.encode("utf-8"))
    print(f"[OK]   {path}")


# ============================================================
# 1. firestore.rules — resource == null в read
# ============================================================
print("=" * 60)
print("1. firestore.rules")
print("=" * 60)

patch("firestore.rules", [
    # groupChats
    (
        """      match /groupChats/{groupId} {
        allow read: if isSignedIn() && (
          request.auth.uid in resource.data.participants
          || isConferenceAdmin()
        );""",
        """      match /groupChats/{groupId} {
        // resource == null — snapshot-listener может подписаться на несуществующий
        // документ (observeGroupMeta до создания). Без этой ветки Firestore
        // отвечает PERMISSION_DENIED, а не not-found.
        allow read: if isSignedIn() && (
          resource == null
          || request.auth.uid in resource.data.participants
          || isConferenceAdmin()
        );"""
    ),
    # directMessages
    (
        """      match /directMessages/{dmId} {
        allow read: if isSignedIn() && (
          request.auth.uid in resource.data.participants
          || isConferenceAdmin()
        );""",
        """      match /directMessages/{dmId} {
        // resource == null — getOrCreateDM подписывается до создания документа.
        allow read: if isSignedIn() && (
          resource == null
          || request.auth.uid in resource.data.participants
          || isConferenceAdmin()
        );"""
    ),
    # comments
    (
        """      match /comments/{commentId} {
        // Чтение: только участники своего клуба или conference_admin.
        // Раньше было isSignedIn() — теперь ограничено клубом.
        allow read: if isSignedIn() && (
          isOwnClub(clubId)
          || isConferenceAdmin()
        );""",
        """      match /comments/{commentId} {
        // Чтение: только участники своего клуба или conference_admin.
        // resource == null — на случай подписки на несуществующий документ.
        allow read: if isSignedIn() && (
          resource == null
          || isOwnClub(clubId)
          || isConferenceAdmin()
        );"""
    ),
])


# ============================================================
# 2. FirestoreCommentDataSource.kt — observePendingComments()
# ============================================================
print()
print("=" * 60)
print("2. FirestoreCommentDataSource.kt")
print("=" * 60)

patch("app/src/main/java/com/pathfinder/hub/data/remote/FirestoreCommentDataSource.kt", [
    (
        """    }.catch { e ->
        Log.e(TAG, "observeApprovedComments flow error", e)
        emit(emptyList())
    }

    /**
     * Записывает комментарий в Firestore.
     * Идемпотентно: если документ уже есть — перезапишет.
     */""",
        """    }.catch { e ->
        Log.e(TAG, "observeApprovedComments flow error", e)
        emit(emptyList())
    }

    /**
     * Очередь ручной модерации — читаем напрямую из Firestore.
     *
     * Раньше ModerationScreen читал из Room, но комментарии пишутся
     * сразу в Firestore и в Room-кэш автора не попадают. Из-за этого
     * директор не видел флаги с чужих устройств.
     *
     * Требуется составной индекс:
     *   collection: comments
     *   fields:     status ASC, createdAt DESC
     * Firestore подскажет ссылку в логах при первом запросе.
     */
    fun observePendingComments(clubId: String): Flow<List<CommentEntity>> = callbackFlow {
        if (clubId.isBlank()) {
            Log.w(TAG, "observePendingComments: clubId пустой")
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = commentsRef(clubId)
            .whereIn("status", listOf("pending_review", "draft", "flagged"))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "pendingComments listener failed", error)
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        mapToEntity(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "mapToEntity(pending) failed ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observePendingComments flow error", e)
        emit(emptyList())
    }

    /**
     * Записывает комментарий в Firestore.
     * Идемпотентно: если документ уже есть — перезапишет.
     */"""
    ),
])


# ============================================================
# 3. ModerationRepository.kt — читаем из Firestore, не из Room
# ============================================================
print()
print("=" * 60)
print("3. ModerationRepository.kt")
print("=" * 60)

patch("app/src/main/java/com/pathfinder/hub/data/repository/ModerationRepository.kt", [
    (
        """    fun observePendingComments(): Flow<List<CommentEntity>> =
        dao.observePendingComments()""",
        """    /**
     * Очередь ручной модерации — источник правды Firestore.
     * Room-версия оставлена рядом (observePendingCommentsLocal)
     * для офлайн-фолбэка / тестов.
     */
    fun observePendingComments(clubId: String): Flow<List<CommentEntity>> =
        firestore.observePendingComments(clubId)

    /** Локальный кэш из Room (автор увидит свои же pending-комментарии офлайн). */
    fun observePendingCommentsLocal(): Flow<List<CommentEntity>> =
        dao.observePendingComments()"""
    ),
])


# ============================================================
# 4. ModerationScreenViewModel.kt — достаём clubId и грузим очередь
# ============================================================
print()
print("=" * 60)
print("4. ModerationScreenViewModel.kt")
print("=" * 60)

patch("app/src/main/java/com/pathfinder/hub/ui/moderation/ModerationScreenViewModel.kt", [
    (
        """    init {
        val userId = sessionManager.getUserId()
        _state.update { it.copy(currentUserId = userId) }
        loadPendingComments()
    }

    private fun loadPendingComments() {
        viewModelScope.launch {
            try {
                moderationRepository.observePendingComments().collect { pendingList ->""",
        """    init {
        val userId = sessionManager.getUserId()
        _state.update { it.copy(currentUserId = userId) }

        if (userId == null) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "Пользователь не авторизован")
            }
        } else {
            viewModelScope.launch {
                val user = userRepository.getUser(userId)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "Клуб не найден")
                    }
                } else {
                    loadPendingComments(clubId)
                }
            }
        }
    }

    private fun loadPendingComments(clubId: String) {
        viewModelScope.launch {
            try {
                moderationRepository.observePendingComments(clubId).collect { pendingList ->"""
    ),
])


print()
print("=" * 60)
print("Готово.")
print("=" * 60)
print("""
Что сделать после запуска скрипта:

1. Задеплоить правила:
     firebase deploy --only firestore:rules

2. Создать составной индекс (Firestore подсветит ссылку в логе приложения):
     collection: comments
     fields:     status ASC, createdAt DESC
   Либо добавить в firestore.indexes.json и задеплоить:
     firebase deploy --only firestore:indexes

3. Пересобрать APK и проверить:
   a) Директор → Участники → "Написать" → чат открывается без PERMISSION_DENIED.
   b) Teen пишет сообщение с матом → status становится "pending_review"
      в clubs/{clubId}/comments/{id} → директор видит его в ModerationScreen.

4. FCM end-to-end (teen ↔ teen) без Cloud Function работать не будет.
   Это следующий шаг — пришли functions/index.js или подтверди,
   что функций нет вообще, и я соберу минимальный Cloud Function.
""")