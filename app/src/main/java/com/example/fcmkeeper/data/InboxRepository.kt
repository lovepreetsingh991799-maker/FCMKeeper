package com.example.fcmkeeper.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One process-wide repository. All access is serialized and performed off the UI thread. */
class InboxRepository(context: Context, databaseName: String = "notification_inbox.db") : AutoCloseable {
    private val database = object : SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE notifications (
                id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL,
                banner_url TEXT, timestamp INTEGER NOT NULL, is_read INTEGER NOT NULL DEFAULT 0
            )""")
            db.execSQL("CREATE INDEX notification_time ON notifications(timestamp DESC)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Add non-destructive migrations here when the schema version changes.
            error("Missing migration from $oldVersion to $newVersion")
        }
    }
    private val _items = MutableStateFlow<List<InboxNotification>>(emptyList())
    val items = _items.asStateFlow()

    @Synchronized
    fun refresh() {
        _items.value = database.readableDatabase.rawQuery(
            "SELECT id,title,description,banner_url,timestamp,is_read FROM notifications ORDER BY timestamp DESC,id DESC", null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(InboxNotification(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    if (cursor.isNull(3)) null else cursor.getString(3), cursor.getLong(4), cursor.getInt(5) == 1
                ))
            }
        }
    }

    /** First delivery wins. A duplicate never resets read state or posts another alert. */
    @Synchronized
    fun insert(item: InboxNotification): Boolean {
        val values = ContentValues().apply {
            put("id", item.id); put("title", item.title); put("description", item.description)
            put("banner_url", item.bannerUrl); put("timestamp", item.timestamp)
            put("is_read", if (item.isRead) 1 else 0)
        }
        val inserted = database.writableDatabase.insertWithOnConflict(
            "notifications", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
        refresh()
        return inserted
    }

    @Synchronized
    fun markRead(id: String) {
        database.writableDatabase.update("notifications", ContentValues().apply { put("is_read", 1) }, "id = ?", arrayOf(id))
        refresh()
    }

    @Synchronized
    fun markAllRead() {
        database.writableDatabase.update("notifications", ContentValues().apply { put("is_read", 1) }, "is_read = 0", null)
        refresh()
    }

    @Synchronized
    fun delete(id: String) {
        database.writableDatabase.delete("notifications", "id = ?", arrayOf(id))
        refresh()
    }

    @Synchronized
    fun clear() {
        database.writableDatabase.delete("notifications", null, null)
        refresh()
    }

    @Synchronized
    override fun close() = database.close()
}