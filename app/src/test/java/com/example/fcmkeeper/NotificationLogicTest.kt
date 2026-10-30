package com.example.fcmkeeper

import com.example.fcmkeeper.data.*
import org.junit.Assert.*
import org.junit.Test

class NotificationLogicTest {
    @Test fun completePayloadPreservesFields() {
        val item = NotificationParser.parse(mapOf("id" to "notification_101", "title" to "New Notification",
            "description" to "Assignment message", "bannerImg" to "https://example.com/banner.jpg", "timestamp" to "1791873000000"))
        assertEquals("notification_101", item.id)
        assertEquals("New Notification", item.title)
        assertEquals("Assignment message", item.description)
        assertEquals("https://example.com/banner.jpg", item.bannerUrl)
        assertEquals(1791873000000L, item.timestamp)
        assertFalse(item.isRead)
    }
    @Test fun missingValuesUseSafeDefaultsAndTransportId() {
        val item = NotificationParser.parse(mapOf("id" to " ", "title" to " "), messageId = "transport-1", now = 1000)
        assertEquals("transport-1", item.id)
        assertEquals("New notification", item.title)
        assertEquals("No description provided.", item.description)
        assertEquals(1000L, item.timestamp)
        assertNull(item.bannerUrl)
    }
    @Test fun invalidTimestampsNeverCrash() {
        listOf("", "no-date", "-4", "0", "999999999999999999999", "253402300800000").forEach {
            assertEquals(1234L, NotificationParser.parse(mapOf("timestamp" to it), now = 1234).timestamp)
        }
    }
    @Test fun unsafeOrBrokenImagesFallBack() {
        listOf("http://example.com/a.jpg", "file:///sdcard/a.jpg", "content://private", "garbage", "https://", "https://bad url").forEach {
            assertNull(NotificationParser.parse(mapOf("bannerImg" to it)).bannerUrl)
        }
    }
    @Test fun notificationPayloadFallbacksAreSupported() {
        val item = NotificationParser.parse(emptyMap(), fallbackTitle = "Title", fallbackDescription = "Body", fallbackImage = "https://example.com/image.png")
        assertEquals("Title", item.title)
        assertEquals("Body", item.description)
        assertNotNull(item.bannerUrl)
        assertTrue(item.id.isNotBlank())
    }
    @Test fun searchAndReadFilterComposeAndSortingIsDeterministic() {
        val items = listOf(
            InboxNotification("a", "Release", "Compose", null, 20, false),
            InboxNotification("b", "Read update", "COMPOSE", null, 30, true),
            InboxNotification("c", "News", "Other", null, 10, false),
        )
        assertEquals(listOf("a"), selectNotifications(items, InboxFilter.UNREAD, InboxSort.NEWEST, " compose ").map { it.id })
        assertEquals(listOf("b", "a", "c"), selectNotifications(items, InboxFilter.ALL, InboxSort.NEWEST, "").map { it.id })
        assertEquals(listOf("c", "a", "b"), selectNotifications(items, InboxFilter.ALL, InboxSort.OLDEST, "").map { it.id })
        assertEquals(listOf("b"), selectNotifications(items, InboxFilter.READ, InboxSort.NEWEST, "").map { it.id })
    }
}