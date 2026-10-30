package com.example.fcmkeeper.messaging

import android.util.Log
import com.example.fcmkeeper.KeeperApplication
import com.example.fcmkeeper.data.NotificationParser
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class KeeperMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as KeeperApplication
        val item = NotificationParser.parse(
            message.data, message.messageId, message.notification?.title,
            message.notification?.body, message.notification?.imageUrl?.toString()
        )
        // Firebase invokes this callback on a worker thread. Persist before posting; no detached job
        // or network image fetch that could outlive the service's short execution window.
        try {
            if (app.repository.insert(item)) app.publisher.post(item)
        } catch (error: android.database.SQLException) {
            Log.e("KeeperMessaging", "Unable to persist incoming notification", error)
        }
    }
    override fun onNewToken(token: String) {
        getSharedPreferences("messaging", MODE_PRIVATE).edit().putString("token", token).apply()
    }
}