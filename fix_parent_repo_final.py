#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ParentRepository — финальная версия с точными сигнатурами.
Перезаписывает целиком.
Запуск:
    python fix_parent_repo_final.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")
PATH = "app/src/main/java/com/pathfinder/hub/data/repository/ParentRepository.kt"

CONTENT = '''package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.ParentDao
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.parent.ParentMeetingEntity
import com.pathfinder.hub.data.local.entity.parent.ParentMessageEntity
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Родительский репозиторий.
 *
 * Сигнатуры соответствуют вызовам из use-case'ов:
 *   - ConfirmPaymentUseCase: confirmPayment(id, status, by, at)
 *   - LinkChildUseCase:      upsertLink(entity)
 *   - SendParentMessageUseCase: upsertMessage(entity)
 *   - revokeLink, moderateMessage, upsertMeeting, upsertPayment — на будущее
 */
@Singleton
class ParentRepository @Inject constructor(
    private val dao: ParentDao,
) {

    // ---------- Дети ----------
    fun observeChildren(parentId: String): Flow<List<ParentChildLinkEntity>> =
        dao.observeChildren(parentId)

    fun observeParents(childId: String): Flow<List<ParentChildLinkEntity>> =
        dao.observeParents(childId)

    suspend fun upsertLink(l: ParentChildLinkEntity) = dao.upsertLink(l)

    suspend fun revokeLink(
        parentId: String,
        childId: String,
        at: Long = System.currentTimeMillis(),
        reason: String? = null,
    ) = dao.revokeLink(parentId, childId, at, reason)

    // ---------- Оплаты ----------
    fun observeChildPayments(childId: String): Flow<List<PaymentEntity>> =
        dao.observeChildPayments(childId)

    fun observeParentPayments(parentId: String): Flow<List<PaymentEntity>> =
        dao.observeParentPayments(parentId)

    suspend fun upsertPayment(p: PaymentEntity) = dao.upsertPayment(p)

    suspend fun confirmPayment(
        id: String,
        status: String,
        by: String,
        at: Long = System.currentTimeMillis(),
    ) = dao.confirmPayment(id, status, by, at)

    // ---------- Сообщения ----------
    fun observeMessages(parentId: String): Flow<List<ParentMessageEntity>> =
        dao.observeMessages(parentId)

    fun observePendingMessages(): Flow<List<ParentMessageEntity>> =
        dao.observePendingMessages()

    suspend fun upsertMessage(m: ParentMessageEntity) = dao.upsertMessage(m)

    suspend fun moderateMessage(
        id: String,
        status: String,
        by: String,
        at: Long = System.currentTimeMillis(),
    ) = dao.moderateMessage(id, status, by, at)

    // ---------- Встречи ----------
    suspend fun upsertMeeting(m: ParentMeetingEntity) = dao.upsertMeeting(m)
}
'''

p = ROOT / PATH
if not p.exists():
    print("[ERROR] не найден: " + PATH); sys.exit(1)

bak = p.with_suffix(p.suffix + ".bak." + STAMP)
shutil.copy2(p, bak)
print("[BAK]  " + bak.name)

p.write_text(CONTENT, encoding="utf-8", newline="\n")
print("[REWRITE] " + PATH)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")