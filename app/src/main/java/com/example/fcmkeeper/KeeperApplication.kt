package com.example.fcmkeeper

import android.app.Application
import com.example.fcmkeeper.data.InboxRepository
import com.example.fcmkeeper.messaging.NotificationPublisher
import com.google.firebase.FirebaseApp

class KeeperApplication : Application() {
    val repository by lazy { InboxRepository(this) }
    val publisher by lazy { NotificationPublisher(this) }
    override fun onCreate() {
        super.onCreate()
        publisher.createChannel()
        // Returns null safely when google-services.json has not been supplied.
        FirebaseApp.initializeApp(this)
    }
}