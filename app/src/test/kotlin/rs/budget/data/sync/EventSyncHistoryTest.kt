package rs.budget.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.RawEventRecord
import rs.budget.data.testDb
import rs.budget.domain.Account
import rs.budget.domain.AccountCreated
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import java.time.Instant
import java.time.ZoneId

class EventSyncHistoryTest {
    private val clock = FixedClock(Instant.parse("2026-10-18T12:00:00Z"), ZoneId.of("UTC"))
    private fun store(id: String): EventStore {
        val db = testDb()
        return EventStore(db, id, clock, Projector(db))
    }

    @Test fun `full snapshot preserves opaque events and blocks subsequent local writes`() = runTest {
        val source = store("one")
        val destination = store("two")
        val unknown = RawEventRecord("new", "one", 1, clock.now().toEpochMilli(), "NewEvent", "{\"type\":\"NewEvent\", \"value\": 42}")
        source.importRaw(listOf(unknown))
        val bytes = EventSyncHistory(source).exportSnapshot()
        val adapter = EventSyncHistory(destination)
        assertTrue(adapter.mergeSnapshot(bytes).requiresUpgrade)
        assertEquals(listOf(unknown), destination.exportRaw())
        assertArrayEquals(bytes, adapter.exportSnapshot())
        try {
            destination.append(AccountCreated(Account("a", "Cash", Currency.RSD, AccountKind.SPENDING)))
            fail("Unsupported history must block writes")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf(unknown), destination.exportRaw())
    }

    @Test fun `malformed or unsupported snapshot leaves local history intact`() = runTest {
        val store = store("one")
        val adapter = EventSyncHistory(store)
        val before = adapter.exportSnapshot()
        for (input in listOf("not json", "{\"version\":2,\"records\":[]}")) {
            try { adapter.mergeSnapshot(input.encodeToByteArray()); fail() } catch (_: InvalidSyncFile) { }
            assertArrayEquals(before, adapter.exportSnapshot())
        }
    }
}
