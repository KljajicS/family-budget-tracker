package rs.budget.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.budget.domain.Account
import rs.budget.domain.AccountCreated
import rs.budget.domain.AccountKind
import rs.budget.domain.Category
import rs.budget.domain.CategoryCreated
import rs.budget.domain.CategoryUpdated
import rs.budget.domain.Currency
import rs.budget.domain.Event
import rs.budget.domain.EventRecord
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionAdded
import rs.budget.domain.TransactionDeleted
import rs.budget.domain.TransactionEdited
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EventStoreTest {
    private val db = testDb()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val store = EventStore(db, deviceId = "dev-1", clock = clock, projector = Projector(db))

    private fun tx(id: String, amount: String, type: TransactionType = TransactionType.EXPENSE) = Transaction(
        id = id, type = type, money = Money.of(amount, Currency.RSD), accountId = "a1", toAccountId = null,
        categoryId = null, date = LocalDate.of(2026, 10, 14), note = "", merchant = "", paidBy = "m1",
        source = TransactionSource.MANUAL,
    )

    private fun at(hour: Int) = Instant.parse("2026-10-14T%02d:00:00Z".format(hour))

    @Test
    fun `append assigns increasing seq and projects account`() = runTest {
        val r1 = store.append(AccountCreated(Account("a1", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val r2 = store.append(AccountCreated(Account("a2", "Card", Currency.RSD, AccountKind.SPENDING)))
        assertEquals(1L, r1.seq)
        assertEquals(2L, r2.seq)
        assertEquals(listOf("Card", "Cash"), db.accountQueries.active().executeAsList().map { it.name })
        assertEquals("AccountCreated", db.eventQueries.allInOrder().executeAsList().first().type)
    }

    @Test
    fun `append never goes back in time even when the phone clock does`() = runTest {
        val r1 = store.append(AccountCreated(Account("a1", "Cash", Currency.RSD, AccountKind.SPENDING)))
        clock.instant = at(8)
        val r2 = store.append(AccountCreated(Account("a2", "Card", Currency.RSD, AccountKind.SPENDING)))
        assertTrue(r2.occurredAt > r1.occurredAt)
    }

    @Test
    fun `edit replaces and delete removes transaction`() = runTest {
        store.append(TransactionAdded(tx("t1", "100")))
        store.append(TransactionEdited(tx("t1", "150")))
        assertEquals("150.00", db.txQueries.all().executeAsList().single().amount)
        store.append(TransactionDeleted("t1"))
        assertEquals(0, db.txQueries.all().executeAsList().size)
    }

    @Test
    fun `older remote edit does not overwrite newer local edit`() = runTest {
        store.append(TransactionAdded(tx("t1", "100")))
        clock.instant = at(12)
        store.append(TransactionEdited(tx("t1", "150")))
        store.applyRemote(listOf(EventRecord("remote-1", "dev-2", 1, at(11), TransactionEdited(tx("t1", "120")))))
        assertEquals("150.00", db.txQueries.all().executeAsList().single().amount)
    }

    @Test
    fun `delete wins over an edit that arrives later`() = runTest {
        store.append(TransactionAdded(tx("t1", "100")))
        clock.instant = at(11)
        store.append(TransactionDeleted("t1"))
        store.applyRemote(listOf(EventRecord("remote-1", "dev-2", 1, at(12), TransactionEdited(tx("t1", "120")))))
        assertEquals(0, db.txQueries.all().executeAsList().size)
    }

    @Test
    fun `a later create does not undo an earlier rename`() = runTest {
        store.append(CategoryCreated(Category("cat-fun", "Fun", "#111111")))
        clock.instant = at(11)
        store.append(CategoryUpdated(Category("cat-fun", "Zabava", "#111111")))
        store.applyRemote(listOf(EventRecord("remote-1", "dev-2", 1, at(12), CategoryCreated(Category("cat-fun", "Fun", "#111111")))))
        assertEquals("Zabava", db.categoryQueries.active().executeAsList().single().name)
    }

    @Test
    fun `applyRemote skips records already seen`() = runTest {
        val rec = EventRecord("remote-1", "dev-2", 1, clock.now(), AccountCreated(Account("a9", "X", Currency.EUR, AccountKind.SAVINGS)))
        store.applyRemote(listOf(rec))
        store.applyRemote(listOf(rec))
        assertEquals(1L, db.eventQueries.count().executeAsOne())
    }

    @Test
    fun `two devices converge whatever the delivery order`() = runTest {
        val events: List<Pair<String, Event>> = listOf(
            "dev-a" to CategoryCreated(Category("c1", "Fun", "#111111")),
            "dev-b" to TransactionAdded(tx("t1", "100")),
            "dev-a" to TransactionEdited(tx("t1", "150")),
            "dev-b" to CategoryUpdated(Category("c1", "Zabava", "#222222")),
            "dev-a" to TransactionAdded(tx("t2", "70")),
            "dev-b" to TransactionDeleted("t1"),
            "dev-a" to TransactionEdited(tx("t1", "999")),
        )
        val seqs = mutableMapOf<String, Long>()
        val records = events.mapIndexed { i, (device, e) ->
            EventRecord("e$i", device, seqs.merge(device, 1L, Long::plus)!!, at(i + 1), e)
        }
        fun tables(db: rs.budget.data.db.BudgetDb) =
            db.txQueries.all().executeAsList().map { it.id to it.amount } to db.categoryQueries.active().executeAsList().map { it.name }

        val a = testDb().also { EventStore(it, "x", clock, Projector(it)).applyRemote(records) }
        val b = testDb().also { d ->
            val s = EventStore(d, "y", clock, Projector(d))
            records.shuffled(java.util.Random(7)).chunked(3).forEach(s::applyRemote)
        }
        assertEquals(tables(a), tables(b))
        assertEquals(listOf("t2" to "70.00") to listOf("Zabava"), tables(a))
    }

    @Test
    fun `rebuild skips events this version cannot read`() = runTest {
        store.append(AccountCreated(Account("a1", "Cash", Currency.RSD, AccountKind.SPENDING)))
        db.eventQueries.insert("future", "dev-9", 1, at(12).toEpochMilli(), "Unknown", """{"type":"Unknown"}""")
        store.rebuild()
        assertEquals(1, db.accountQueries.active().executeAsList().size)
    }
}
