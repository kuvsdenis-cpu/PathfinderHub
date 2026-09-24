package com.pathfinder.hub.data.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.writeToFile
import aws.smithy.kotlin.runtime.net.url.Url
import com.pathfinder.hub.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun uploadFile(
        localUri: Uri,
        folder: String = "reports",
        extension: String = "jpg"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(localUri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Cannot open Uri"))

            if (bytes.isEmpty()) {
                return@withContext Result.failure(Exception("Empty file"))
            }

            val key = "$folder/${UUID.randomUUID()}.$extension"

            client.putObject(
                PutObjectRequest {
                    this.bucket = bucketName
                    this.key = key
                    this.body = ByteStream.fromBytes(bytes)
                    this.contentType = contentTypeFor(extension)
                }
            )

            Log.d(TAG, "Uploaded: $key (${bytes.size} bytes)")
            Result.success(key)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(e)
        }
    }

    suspend fun downloadFile(key: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("download_", ".tmp", context.cacheDir)

            client.getObject(
                GetObjectRequest {
                    this.bucket = bucketName
                    this.key = key
                }
            ) { response ->
                response.body?.writeToFile(tempFile)
            }

            val bytes = tempFile.readBytes()
            tempFile.delete()

            if (bytes.isEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            Result.success(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }

    fun getPublicUrl(key: String): String {
        return "https://$bucketName.storage.yandexcloud.net/$key"
    }

    private fun contentTypeFor(extension: String): String = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    companion object {
        private const val TAG = "YandexStorage"
    }
}