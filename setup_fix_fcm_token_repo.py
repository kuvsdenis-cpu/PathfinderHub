#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Фикс FcmTokenRepository: return в expression body.
"""

import shutil
import sys
from pathlib import Path


def find_project_root() -> Path:
    here = Path.cwd()
    for p in [here] + list(here.parents):
        if (p / "settings.gradle.kts").exists() or (p / "settings.gradle").exists():
            return p
    print("❌ Не найден корень проекта.")
    sys.exit(1)


ROOT = find_project_root()
PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "pathfinder" / "hub"


CONTENT = '''package com.pathfinder.hub.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохраняет FCM-токены в Firestore.
 * Путь: users/{uid}/fcmTokens/{token}
 */
@Singleton
class FcmTokenRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun registerCurrentToken(userId: String): Result<String> {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isBlank()) {
                Result.failure(IllegalStateException("Пустой FCM-токен"))
            } else {
                saveToken(userId, token)
                Result.success(token)
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerCurrentToken failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveToken(userId: String, token: String): Result<Unit> {
        return try {
            val data = mapOf(
                "userId" to userId,
                "token" to token,
                "platform" to "android",
                "deviceId" to android.os.Build.MODEL,
                "updatedAt" to Timestamp(Date()),
            )
            firestore.collection("users")
                .document(userId)
                .collection("fcmTokens")
                .document(token)
                .set(data)
                .await()
            Log.d(TAG, "FCM token saved: ${token.take(12)}…")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "saveToken failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "FcmTokenRepo"
    }
}
'''


def main():
    print("=" * 60)
    print("  Fix FcmTokenRepository (return в expression body)")
    print("=" * 60)

    f = PKG / "data/repository/FcmTokenRepository.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        sys.exit(1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(CONTENT, encoding="utf-8")
    print("✅ FcmTokenRepository.kt переписан")
    print("\nСборка: .\\gradlew assembleDebug")
    print("=" * 60)


if __name__ == "__main__":
    main()