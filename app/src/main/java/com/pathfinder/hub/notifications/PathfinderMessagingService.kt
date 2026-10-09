package com.pathfinder.hub.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.FcmTokenRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Принимает push-уведомления от FCM (наша Yandex Cloud Function).
 */
@AndroidEntryPoint
class PathfinderMessagingService : FirebaseMessagingService() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var tokenRepo: FcmTokenRepository
    @Inject lateinit var notifier: ChatNotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(12)}…")
        val uid = sessionManager.getUserId() ?: return
        scope.launch { tokenRepo.saveToken(uid, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Push: from=${message.from}, data=${message.data}")

        val data = message.data
        val type = data["type"] ?: "dm"
        val targetId = data["targetId"] ?: ""
        val otherUid = data["otherUid"] ?: ""
        val title = message.notification?.title ?: data["title"] ?: "Новое сообщение"
        val body = message.notification?.body ?: data["body"] ?: ""

        when (type) {
            "dm" -> notifier.notifyDm(targetId, otherUid, title, body)
            "group" -> notifier.notifyGroup(targetId, title, body)
            "club_chat" -> notifier.notifyClubChat(targetId, title, body)
            else -> Log.w(TAG, "Unknown push type: $type")
        }
    }

    companion object {
        private const val TAG = "PathfinderFCM"
    }
}
