package com.example.fcmkeeper

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.example.fcmkeeper.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class InboxRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "test-${UUID.randomUUID()}.db"
    private lateinit var repository: InboxRepository
    private val item = InboxNotification("101", "Hello", "Description", null, 1000)
    @Before fun setup() { repository = InboxRepository(context, name) }
    @After fun cleanup() { repository.close(); context.deleteDatabase(name) }

    @Test fun duplicateDoesNotOverwriteReadStateAndSurvivesReopen() {
        assertTrue(repository.insert(item))
        repository.markRead(item.id)
        assertFalse(repository.insert(item.copy(title = "Duplicate")))
        repository.close()
        repository = InboxRepository(context, name)
        repository.refresh()
        assertEquals(1, repository.items.value.size)
        assertEquals("Hello", repository.items.value.single().title)
        assertTrue(repository.items.value.single().isRead)
    }
    @Test fun concurrentDuplicateDeliveryIsAtomic() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = (1..12).map { pool.submit<Boolean> { repository.insert(item) } }.map { it.get() }
            assertEquals(1, results.count { it })
            assertEquals(1, repository.items.value.size)
        } finally { pool.shutdown() }
    }
    @Test fun markAllDeleteAndClearPersist() {
        repository.insert(item)
        repository.insert(item.copy(id = "102", timestamp = 2000))
        repository.markAllRead()
        assertTrue(repository.items.value.all { it.isRead })
        repository.delete("101")
        assertEquals("102", repository.items.value.single().id)
        repository.clear()
        repository.close()
        repository = InboxRepository(context, name)
        repository.refresh()
        assertTrue(repository.items.value.isEmpty())
    }
}