package com.pathfinder.hub.domain.service

import android.util.Log
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.ui.storage.FileUploadContext
import com.pathfinder.hub.ui.storage.FileUploadResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUploadResultHandler @Inject constructor(
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val sessionManager: SessionManager
) {

    suspend fun handle(result: FileUploadResult): Boolean {
        val userId = sessionManager.getUserId()
        if (userId.isNullOrBlank()) {
            Log.w(TAG, "no userId")
            return false
        }

        return try {
            when (result.context) {
                FileUploadContext.LEVEL_REQUIREMENT -> {
                    levelRepository.attachReportToRequirement(
                        userId = userId,
                        requirementId = result.contextId,
                        fileUrl = result.fileUrl,
                        fileName = result.fileName
                    )
                    true
                }
                FileUploadContext.HONOR_REQUIREMENT -> {
                    honorRepository.attachReportToRequirement(
                        userId = userId,
                        requirementId = result.contextId,
                        fileUrl = result.fileUrl,
                        fileName = result.fileName
                    )
                    true
                }
                FileUploadContext.CHAT -> {
                    Log.d(TAG, "CHAT handled in ClubChatScreen")
                    false
                }
                FileUploadContext.TASK,
                FileUploadContext.AVATAR,
                FileUploadContext.EVENT,
                FileUploadContext.NEWSPAPER,
                FileUploadContext.GENERIC -> {
                    Log.d(TAG, "TODO: " + result.context + " - " + result.fileUrl)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handle failed for " + result.context, e)
            false
        }
    }

    companion object {
        private const val TAG = "FileUploadResultHandler"
    }
}
