package com.pathfinder.hub.data.repository

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
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.io.File
import java.net.URI
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun uploadFile(
        file: File,
        objectKey: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("StorageRepository", "Uploading ${file.name} to $bucketName/$objectKey")

            val request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(getContentType(file.extension))
                .build()

            s3Client.putObject(request, RequestBody.fromFile(file))

            val presignedUrl = generatePresignedUrl(objectKey)

            Log.d("StorageRepository", "Upload successful, presigned URL: $presignedUrl")
            Result.success(presignedUrl)
        } catch (e: Exception) {
            Log.e("StorageRepository", "Upload failed", e)
            Result.failure(e)
        }
    }

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