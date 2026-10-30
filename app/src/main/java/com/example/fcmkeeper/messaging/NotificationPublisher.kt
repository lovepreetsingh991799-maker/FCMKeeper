package com.example.fcmkeeper.messaging

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.fcmkeeper.MainActivity
import com.example.fcmkeeper.data.InboxNotification

class NotificationPublisher(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "assignment"
        const val OPEN_INBOX = "com.example.fcmkeeper.OPEN_INBOX"
    }
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Assignment", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifications saved to your FCM Keeper inbox"
                }
            )
        }
    }
    fun enabled(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun post(item: InboxNotification): Boolean {
        createChannel()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!enabled()) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            action = OPEN_INBOX
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(item.title)
            .setContentText(item.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.description))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setWhen(item.timestamp).setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        return try {
            // Tag + constant ID avoids String.hashCode collisions between different payload IDs.
            NotificationManagerCompat.from(context).notify(item.id, 0, notification)
            true
        } catch (_: SecurityException) { false }
    }
    fun cancel(id: String) = NotificationManagerCompat.from(context).cancel(id, 0)
    fun cancelAll() = NotificationManagerCompat.from(context).cancelAll()
}