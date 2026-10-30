package com.example.fcmkeeper.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.fcmkeeper.KeeperApplication
import com.example.fcmkeeper.data.NotificationParser
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class InboxViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as KeeperApplication
    val items = app.repository.items
    val selectedId = saved.getStateFlow<String?>("selectedId", null)
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _loadError = MutableStateFlow(false)
    val loadError = _loadError.asStateFlow()
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    private val _token = MutableStateFlow<String?>(null)
    val token = _token.asStateFlow()
    private val _firebaseStatus = MutableStateFlow("Checking Firebase configuration…")
    val firebaseStatus = _firebaseStatus.asStateFlow()
    val firebaseConfigured get() = FirebaseApp.getApps(app).isNotEmpty()

    init { reload(); refreshToken() }

    private fun operation(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { events.send("Couldn't save this change. Please try again.") }
    }

    fun reload() = viewModelScope.launch {
        _loading.value = true
        _loadError.value = false
        try { withContext(Dispatchers.IO) { app.repository.refresh() } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { _loadError.value = true }
        finally { _loading.value = false }
    }
    fun open(id: String) = operation {
        withContext(Dispatchers.IO) { app.repository.markRead(id) }
        app.publisher.cancel(id)
        saved["selectedId"] = id
    }
    fun inbox() { saved["selectedId"] = null }
    fun markAllRead() = operation {
        withContext(Dispatchers.IO) { app.repository.markAllRead() }
        app.publisher.cancelAll()
        events.send("All notifications marked as read")
    }
    fun delete(id: String) = operation {
        withContext(Dispatchers.IO) { app.repository.delete(id) }
        app.publisher.cancel(id)
        if (selectedId.value == id) inbox()
        events.send("Notification deleted")
    }
    fun clear() = operation {
        withContext(Dispatchers.IO) { app.repository.clear() }
        app.publisher.cancelAll()
        inbox()
        events.send("Inbox cleared")
    }
    fun addLocalTest() = operation {
        val item = NotificationParser.parse(mapOf(
            "id" to "local-test-${UUID.randomUUID()}",
            "title" to "Your inbox is ready",
            "description" to "This is a local test notification. Open it to mark it as read. Your inbox and read states stay on this device after you close the app. Connect Firebase to receive real push messages.",
        ))
        withContext(Dispatchers.IO) { app.repository.insert(item) }
        val posted = app.publisher.post(item)
        events.send(if (posted) "Local test saved and system notification posted" else "Local test saved. Enable notifications to see system alerts.")
    }
    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val extras = intent.extras
        if (intent.action == com.example.fcmkeeper.messaging.NotificationPublisher.OPEN_INBOX ||
            extras?.containsKey("google.message_id") == true) inbox()
        // Mixed notification+data messages bypass onMessageReceived in the background.
        // Recover their custom data on tap without reposting the already displayed alert.
        if (extras != null && (extras.containsKey("id") || extras.containsKey("google.message_id"))) {
            val data = listOf("id", "title", "description", "bannerImg", "timestamp")
                .mapNotNull { key -> extras.getString(key)?.let { key to it } }.toMap()
            operation {
                withContext(Dispatchers.IO) {
                    app.repository.insert(NotificationParser.parse(data, extras.getString("google.message_id")))
                }
            }
        }
    }
    fun refreshToken() {
        if (!firebaseConfigured) {
            _firebaseStatus.value = "Firebase is not connected. Add your google-services.json and rebuild. Local inbox testing is available."
            return
        }
        _firebaseStatus.value = "Getting this device's FCM token…"
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                _token.value = task.result
                _firebaseStatus.value = "Firebase connected. Use this device token to send a test push."
            } else {
                _firebaseStatus.value = "Token unavailable. Check your internet connection, Firebase configuration and Google Play services, then retry."
            }
        }
    }
}