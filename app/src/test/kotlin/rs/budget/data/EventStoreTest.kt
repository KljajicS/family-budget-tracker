package rs.budget.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.budget.domain.Account
import rs.budget.domain.AccountCreated
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
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

    @Test
    fun `append assigns increasing seq and projects account`() = runTest {
        val r1 = store.append(AccountCreated(Account("a1", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val r2 = store.append(AccountCreated(Account("a2", "Card", Currency.RSD, AccountKind.SPENDING)))
        assertEquals(1L, r1.seq)
        assertEquals(2L, r2.seq)
        assertEquals(listOf("Card", "Cash"), db.accountQueries.active().executeAsList().map { it.name })
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
        clock.instant = Instant.parse("2026-10-14T12:00:00Z")
        store.append(TransactionEdited(tx("t1", "150")))
        val older = EventRecord(
            id = "remote-1", deviceId = "dev-2", seq = 1,
            occurredAt = Instant.parse("2026-10-14T11:00:00Z"),
            event = TransactionEdited(tx("t1", "120")),
        )
        store.applyRemote(listOf(older))
        assertEquals("150.00", db.txQueries.all().executeAsList().single().amount)
    }

    @Test
    fun `applyRemote skips records already seen`() = runTest {
        val rec = EventRecord("remote-1", "dev-2", 1, clock.now(), AccountCreated(Account("a9", "X", Currency.EUR, AccountKind.SAVINGS)))
        store.applyRemote(listOf(rec))
        store.applyRemote(listOf(rec))
        assertEquals(1L, db.eventQueries.count().executeAsOne())
    }

    @Test
    fun `rebuild reproduces read tables from the log in deterministic order`() = runTest {
        val t = clock.now()
        val fromB = EventRecord("b", "dev-b", 1, t, TransactionEdited(tx("t1", "200")))
        val fromA = EventRecord("a", "dev-a", 1, t, TransactionAdded(tx("t1", "100")))
        store.applyRemote(listOf(fromB, fromA))
        val afterApply = db.txQueries.all().executeAsList().single().amount
        store.rebuild()
        assertEquals(afterApply, db.txQueries.all().executeAsList().single().amount)
        assertEquals("200.00", afterApply)
        assertNull(db.txQueries.editedAt("missing").executeAsOneOrNull())
    }
}
