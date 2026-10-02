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
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.net.URI
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
            // ✅ КРИТИЧНО: используем UrlConnectionHttpClient вместо ApacheHttpClient
            .httpClientBuilder(UrlConnectionHttpClient.builder())
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

            val fileUrl = "https://storage.yandexcloud.net/$bucketName/$objectKey"
            Log.d("StorageRepository", "Upload successful: $fileUrl")
            Result.success(fileUrl)
        } catch (e: Exception) {
            Log.e("StorageRepository", "Upload failed", e)
            Result.failure(e)
        }
    }

    private fun getContentType(extension: String): String {
        return when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }
}