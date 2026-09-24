#!/usr/bin/env python3
"""
Генератор Yandex Object Storage интеграции.
Создаёт YandexStorageService.kt, обновляет build.gradle.kts и local.properties.
"""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent

# ============================================================
# 1. YandexStorageService.kt
# ============================================================

STORAGE_SERVICE = '''package com.pathfinder.hub.data.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.net.url.Url
import com.pathfinder.hub.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над Yandex Object Storage (S3-совместимое API).
 * Ключи читаются из local.properties → BuildConfig.
 */
@Singleton
class YandexStorageService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val bucketName = BuildConfig.YANDEX_BUCKET_NAME
    private val endpointUrl = "https://storage.yandexcloud.net"
    private val region = "ru-central1"

    private val client: S3Client by lazy {
        S3Client {
            region = this@YandexStorageService.region
            endpointUrl = Url.parse(this@YandexStorageService.endpointUrl)
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = BuildConfig.YANDEX_ACCESS_KEY_ID
                secretAccessKey = BuildConfig.YANDEX_SECRET_ACCESS_KEY
            }
        }
    }

    /**
     * Загрузить файл из локального Uri.
     * Возвращает ключ объекта в бакете.
     */
    suspend fun uploadFile(
        localUri: Uri,
        folder: String = "reports",
        extension: String = "jpg"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(localUri)
                ?: return@withContext Result.failure(Exception("Cannot open Uri"))

            val bytes = inputStream.readBytes()
            inputStream.close()

            if (bytes.isEmpty()) {
                return@withContext Result.failure(Exception("Empty file"))
            }

            val key = "$folder/${UUID.randomUUID()}.$extension"

            client.putObject(
                PutObjectRequest {
                    this.bucket = bucketName
                    this.key = key
                    this.body = ByteStream.fromBytes(bytes)
                    this.contentType = "image/$extension"
                }
            )

            Log.d(TAG, "Uploaded: $key (${bytes.size} bytes)")
            Result.success(key)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(e)
        }
    }

    /**
     * Скачать файл по ключу.
     */
    suspend fun downloadFile(key: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val response = client.getObject(
                GetObjectRequest {
                    this.bucket = bucketName
                    this.key = key
                }
            )
            val bytes = response.body?.toByteArray()
                ?: return@withContext Result.failure(Exception("Empty response"))
            Result.success(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }

    /**
     * Публичный URL объекта (для отображения без авторизации,
     * если бакет публичный).
     */
    fun getPublicUrl(key: String): String {
        return "https://$bucketName.storage.yandexcloud.net/$key"
    }

    companion object {
        private const val TAG = "YandexStorage"
    }
}
'''

def write_storage_service():
    path = ROOT / "app" / "src" / "main" / "java" / "com" / "pathfinder" / "hub" / "data" / "sync" / "YandexStorageService.kt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(STORAGE_SERVICE, encoding="utf-8")
    print(f"OK {path.relative_to(ROOT)}")


# ============================================================
# 2. Обновление app/build.gradle.kts
# ============================================================

def update_build_gradle():
    path = ROOT / "app" / "build.gradle.kts"
    text = path.read_text(encoding="utf-8")

    # 2.1. Добавить buildConfigField в defaultConfig
    if "YANDEX_ACCESS_KEY_ID" not in text:
        # Вставить в defaultConfig перед закрывающей скобкой
        pattern = r'(defaultConfig\s*\{)(.*?)(\n\s*\})'
        def insert_config(match):
            head = match.group(1)
            body = match.group(2)
            tail = match.group(3)
            additions = '''
        // Yandex Object Storage
        buildConfigField("String", "YANDEX_ACCESS_KEY_ID", "\\"${project.findProperty("YANDEX_ACCESS_KEY_ID") ?: ""}\\"")
        buildConfigField("String", "YANDEX_SECRET_ACCESS_KEY", "\\"${project.findProperty("YANDEX_SECRET_ACCESS_KEY") ?: ""}\\"")
        buildConfigField("String", "YANDEX_BUCKET_NAME", "\\"${project.findProperty("YANDEX_BUCKET_NAME") ?: "pathfinder-hub-media"}\\"")
'''
            return head + body + additions + tail

        text = re.sub(pattern, insert_config, text, count=1, flags=re.DOTALL)

    # 2.2. Добавить AWS SDK в dependencies
    if "aws.sdk.kotlin" not in text:
        pattern = r'(dependencies\s*\{)'
        additions = '''dependencies {

    // Yandex Object Storage (S3-совместимое API)
    implementation("aws.sdk.kotlin:s3:1.0.22")
    implementation("aws.smithy.kotlin:http-client-engine-okhttp:1.0.22")
'''
        text = re.sub(pattern, additions, text, count=1)

    path.write_text(text, encoding="utf-8")
    print(f"OK {path.relative_to(ROOT)} (обновлён)")


# ============================================================
# 3. Обновление local.properties
# ============================================================

def update_local_properties():
    path = ROOT / "local.properties"
    if not path.exists():
        print(f"WARN {path.relative_to(ROOT)} не найден — создаю")
        path.write_text("", encoding="utf-8")

    text = path.read_text(encoding="utf-8")

    additions = []
    if "YANDEX_ACCESS_KEY_ID" not in text:
        additions.append("YANDEX_ACCESS_KEY_ID=YCAJEDKx4Ggt-QY6pakwuwUDd")
    if "YANDEX_SECRET_ACCESS_KEY" not in text:
        additions.append("YANDEX_SECRET_ACCESS_KEY=ВАШ_СЕКРЕТНЫЙ_КЛЮЧ")
    if "YANDEX_BUCKET_NAME" not in text:
        additions.append("YANDEX_BUCKET_NAME=pathfinder-hub-media")

    if additions:
        text = text.rstrip() + "\n\n# Yandex Object Storage\n" + "\n".join(additions) + "\n"
        path.write_text(text, encoding="utf-8")
        print(f"OK {path.relative_to(ROOT)} (обновлён)")
        print("  ВНИМАНИЕ: замените ВАШ_СЕКРЕТНЫЙ_КЛЮЧ на реальный Secret Key")
    else:
        print(f"OK {path.relative_to(ROOT)} (уже настроен)")


# ============================================================
# 4. .gitignore
# ============================================================

def ensure_gitignore():
    path = ROOT / ".gitignore"
    if not path.exists():
        path.write_text("local.properties\n", encoding="utf-8")
        print(f"OK {path.relative_to(ROOT)} (создан)")
        return

    text = path.read_text(encoding="utf-8")
    if "local.properties" not in text:
        text = text.rstrip() + "\nlocal.properties\n"
        path.write_text(text, encoding="utf-8")
        print(f"OK {path.relative_to(ROOT)} (обновлён)")
    else:
        print(f"OK {path.relative_to(ROOT)} (уже содержит local.properties)")


# ============================================================
# ЗАПУСК
# ============================================================

if __name__ == "__main__":
    print("=" * 60)
    print("  Yandex Object Storage — генератор")
    print("=" * 60)
    print()

    write_storage_service()
    update_build_gradle()
    update_local_properties()
    ensure_gitignore()

    print()
    print("=" * 60)
    print("  ГОТОВО")
    print("=" * 60)
    print()
    print("Следующие шаги:")
    print("1. Откройте local.properties")
    print("2. Замените ВАШ_СЕКРЕТНЫЙ_КЛЮЧ на реальный Secret Key")
    print("3. Синхронизируйте Gradle (File → Sync)")
    print("4. Соберите: .\\gradlew clean assembleDebug")