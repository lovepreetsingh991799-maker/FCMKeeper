package com.example.fcmkeeper

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.fcmkeeper.data.InboxNotification
import com.example.fcmkeeper.messaging.NotificationPublisher
import org.junit.*
import org.junit.Assert.*

class InboxFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<KeeperApplication>()
    @Before fun setup() {
        app.repository.clear()
        app.repository.insert(InboxNotification("ui-test", "Test delivery", "A message to inspect", null, System.currentTimeMillis()))
    }
    @After fun cleanup() { app.repository.clear(); app.publisher.cancelAll() }

    @Test fun openingMarksReadAndRecreationPreservesDetail() {
        compose.onNodeWithText("Test delivery").performClick()
        compose.onNodeWithText("Saved in your inbox").assertIsDisplayed()
        assertTrue(app.repository.items.value.single().isRead)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Saved in your inbox").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.onNodeWithText("READ").assertIsDisplayed()
    }
    @Test fun systemNotificationIntentReturnsToInboxWithoutReadingItem() {
        compose.onNodeWithText("Test delivery").performClick()
        app.repository.insert(InboxNotification("unread", "Second delivery", "Still unread", null, System.currentTimeMillis()))
        compose.activityRule.scenario.onActivity {
            it.startActivity(Intent(it, MainActivity::class.java).setAction(NotificationPublisher.OPEN_INBOX)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        compose.onNodeWithText("Your inbox").assertIsDisplayed()
        assertFalse(app.repository.items.value.first { it.id == "unread" }.isRead)
    }
    @Test fun deletionRequiresConfirmation() {
        compose.onNodeWithContentDescription("Delete Test delivery").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, app.repository.items.value.size)
        compose.onNodeWithContentDescription("Delete Test delivery").performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.onNodeWithText("A fresh start").assertIsDisplayed()
        assertTrue(app.repository.items.value.isEmpty())
    }
}