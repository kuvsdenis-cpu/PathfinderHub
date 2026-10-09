#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Реальный прогресс загрузки в StorageRepository.
- Файлы <= 5 МБ: обычный putObject + onProgress(1.0) в конце.
- Файлы > 5 МБ: multipart upload (CreateMultipartUpload + UploadPart + CompleteMultipartUpload).
  onProgress вызывается после каждой части.
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
import com.pathfinder.hub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Работа с Yandex Object Storage.
 *
 * Загрузка файлов:
 *   - файлы <= 5 МБ   -> обычный putObject, onProgress(1.0) в конце
 *   - файлы >  5 МБ   -> multipart upload частями по 5 МБ,
 *                        onProgress(part / total) после каждой части
 *
 * Это даёт реальный прогресс вместо заглушки.
 */
@Singleton
class StorageRepository @Inject constructor() {

    private val s3Client by lazy {
        S3Client.builder()
            .region(Region.of("ru-central1"))
            .endpointOverride(URI.create("https://storage.yandexcloud.net"))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        BuildConfig.YANDEX_ACCESS_KEY_ID,
                        BuildConfig.YANDEX_SECRET_ACCESS_KEY
                    )
                )
            )
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build()
    }

    private val s3Presigner by lazy {
        S3Presigner.builder()
            .region(Region.of("ru-central1"))
            .endpointOverride(URI.create("https://storage.yandexcloud.net"))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        BuildConfig.YANDEX_ACCESS_KEY_ID,
                        BuildConfig.YANDEX_SECRET_ACCESS_KEY
                    )
                )
            )
            .build()
    }

    private val bucketName = BuildConfig.YANDEX_BUCKET_NAME

    companion object {
        private const val TAG = "StorageRepository"

        /** Порог для перехода на multipart (5 МБ). */
        private const val MULTIPART_THRESHOLD = 5L * 1024 * 1024

        /** Размер одной части (5 МБ). Минимум по S3 = 5 МБ, кроме последней. */
        private const val PART_SIZE = 5L * 1024 * 1024
    }

    // ============================================================
    // ЗАГРУЗКА С ПРОГРЕССОМ
    // ============================================================

    suspend fun uploadFile(
        file: File,
        objectKey: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Upload start: ${file.name} (${file.length()} bytes) -> $objectKey")

            if (file.length() <= MULTIPART_THRESHOLD) {
                uploadSmall(file, objectKey, onProgress)
            } else {
                uploadMultipart(file, objectKey, onProgress)
            }

            val presignedUrl = generatePresignedUrl(objectKey)
            Log.d(TAG, "Upload OK, presigned URL generated")
            Result.success(presignedUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: $objectKey", e)
            Result.failure(e)
        }
    }

    /**
     * Маленький файл — один putObject.
     * Прогресс: 0.1 -> 0.3 -> 1.0 (грубо, но быстро).
     */
    private fun uploadSmall(file: File, objectKey: String, onProgress: (Float) -> Unit) {
        onProgress(0.1f)

        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .contentType(getContentType(file.extension))
            .build()

        onProgress(0.3f)
        s3Client.putObject(request, RequestBody.fromFile(file))
        onProgress(1.0f)

        Log.d(TAG, "Small upload OK: $objectKey")
    }

    /**
     * Большой файл — multipart upload.
     *
     * Алгоритм:
     *   1. CreateMultipartUpload -> uploadId
     *   2. Для каждой части: UploadPart -> ETag
     *      После каждой части вызываем onProgress(partNumber / totalParts)
     *   3. CompleteMultipartUpload со списком частей
     *
     * При ошибке -> AbortMultipartUpload, чтобы не платить за мусор в бакете.
     */
    private fun uploadMultipart(
        file: File,
        objectKey: String,
        onProgress: (Float) -> Unit
    ) {
        val totalSize = file.length()
        val totalParts = ((totalSize + PART_SIZE - 1) / PART_SIZE).toInt()
        Log.d(TAG, "Multipart: $totalParts parts of $PART_SIZE bytes, total=$totalSize")

        val createResponse = s3Client.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(getContentType(file.extension))
                .build()
        )
        val uploadId = createResponse.uploadId()
        val completedParts = mutableListOf<CompletedPart>()

        try {
            RandomAccessFile(file, "r").use { raf ->
                for (partNumber in 1..totalParts) {
                    val start = (partNumber - 1) * PART_SIZE
                    val size = minOf(PART_SIZE, totalSize - start).toInt()

                    val buffer = ByteArray(size)
                    raf.seek(start)
                    raf.readFully(buffer)

                    val partResponse = s3Client.uploadPart(
                        UploadPartRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .uploadId(uploadId)
                            .partNumber(partNumber)
                            .contentLength(size.toLong())
                            .build(),
                        RequestBody.fromBytes(buffer)
                    )

                    completedParts.add(
                        CompletedPart.builder()
                            .partNumber(partNumber)
                            .eTag(partResponse.eTag())
                            .build()
                    )

                    val fraction = partNumber.toFloat() / totalParts
                    onProgress(fraction)
                    Log.d(TAG, "Part $partNumber/$totalParts OK (${size} bytes), progress=${(fraction * 100).toInt()}%")
                }
            }

            s3Client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .multipartUpload(
                        CompletedMultipartUpload.builder()
                            .parts(completedParts)
                            .build()
                    )
                    .build()
            )

            Log.d(TAG, "Multipart upload OK: $objectKey")
        } catch (e: Exception) {
            Log.e(TAG, "Multipart failed, aborting uploadId=$uploadId", e)
            try {
                s3Client.abortMultipartUpload(
                    AbortMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .uploadId(uploadId)
                        .build()
                )
                Log.d(TAG, "Aborted multipart upload")
            } catch (abortError: Exception) {
                Log.e(TAG, "Abort failed", abortError)
            }
            throw e
        }
    }

    // ============================================================
    // PRESIGNED URL
    // ============================================================

    fun generatePresignedUrl(
        objectKey: String,
        duration: Duration = Duration.ofDays(7)
    ): String {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(duration)
            .getObjectRequest(getObjectRequest)
            .build()

        return s3Presigner.presignGetObject(presignRequest).url().toString()
    }

    fun buildLevelImageUrl(relativePath: String): String {
        if (relativePath.startsWith("http://") ||
            relativePath.startsWith("https://") ||
            relativePath.startsWith("file://")
        ) {
            return relativePath
        }
        val key = if (relativePath.startsWith("levels_images/")) {
            relativePath
        } else {
            "levels_images/$relativePath"
        }
        return getPublicUrl(key)
    }

    fun getPublicUrl(objectKey: String): String =
        "https://storage.yandexcloud.net/$bucketName/$objectKey"

    // ============================================================
    // MIME-TYPE
    // ============================================================

    private fun getContentType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "heic", "heif" -> "image/heic"

            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "m4v" -> "video/x-m4v"

            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "flac" -> "audio/flac"
            "amr" -> "audio/amr"

            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "rtf" -> "application/rtf"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "epub" -> "application/epub+zip"

            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"

            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"

            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"

            else -> "application/octet-stream"
        }
    }
}
'''


def main():
    print("=" * 60)
    print("  Реальный прогресс загрузки в StorageRepository")
    print("=" * 60)

    f = PKG / "data/repository/StorageRepository.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        sys.exit(1)

    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(CONTENT, encoding="utf-8")
    print("✅ StorageRepository.kt перезаписан (.bak сохранён)")
    print("\nЧто изменилось:")
    print("  - файлы <= 5 МБ: putObject + onProgress(1.0) в конце")
    print("  - файлы >  5 МБ: multipart по 5 МБ")
    print("                   onProgress(part/total) после каждой части")
    print("  - при ошибке multipart -> AbortMultipartUpload")
    print("\nFileUploadViewModel уже правильно передаёт onProgress.")
    print("FileUploadScreen уже показывает LinearProgressIndicator.")
    print("Ничего больше менять не надо.")
    print("\nСборка: .\\gradlew assembleDebug")
    print("=" * 60)


if __name__ == "__main__":
    main()