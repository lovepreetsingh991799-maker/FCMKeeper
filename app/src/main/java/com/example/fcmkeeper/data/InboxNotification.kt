package com.example.fcmkeeper.data

import java.net.URI
import java.util.UUID

data class InboxNotification(
    val id: String,
    val title: String,
    val description: String,
    val bannerUrl: String?,
    val timestamp: Long,
    val isRead: Boolean = false,
)

/** Pure parser; transport IDs keep malformed payload retries idempotent. */
object NotificationParser {
    fun parse(
        data: Map<String, String>,
        messageId: String? = null,
        fallbackTitle: String? = null,
        fallbackDescription: String? = null,
        fallbackImage: String? = null,
        now: Long = System.currentTimeMillis(),
    ): InboxNotification {
        fun String?.nonBlank() = this?.trim()?.takeIf { it.isNotEmpty() }
        val banner = (data["bannerImg"].nonBlank() ?: fallbackImage.nonBlank())?.takeIf {
            runCatching { val uri = URI(it); uri.scheme == "https" && !uri.host.isNullOrBlank() }.getOrDefault(false)
        }
        // Accept epoch milliseconds only; reject negative, overflowing and implausible dates.
        val timestamp = data["timestamp"]?.trim()?.toLongOrNull()
            ?.takeIf { it in 1..253402300799999L } ?: now
        return InboxNotification(
            id = data["id"].nonBlank() ?: messageId.nonBlank() ?: UUID.randomUUID().toString(),
            title = data["title"].nonBlank() ?: fallbackTitle.nonBlank() ?: "New notification",
            description = data["description"].nonBlank() ?: fallbackDescription.nonBlank() ?: "No description provided.",
            bannerUrl = banner,
            timestamp = timestamp,
        )
    }
}

enum class InboxFilter { ALL, UNREAD, READ }
enum class InboxSort { NEWEST, OLDEST }

fun selectNotifications(
    items: List<InboxNotification>, filter: InboxFilter, sort: InboxSort, query: String,
): List<InboxNotification> {
    val search = query.trim()
    val filtered = items.filter {
        (filter == InboxFilter.ALL || it.isRead == (filter == InboxFilter.READ)) &&
            (search.isEmpty() || it.title.contains(search, true) || it.description.contains(search, true))
    }
    val order = compareBy<InboxNotification> { it.timestamp }.thenBy { it.id }
    return filtered.sortedWith(if (sort == InboxSort.NEWEST) order.reversed() else order)
}