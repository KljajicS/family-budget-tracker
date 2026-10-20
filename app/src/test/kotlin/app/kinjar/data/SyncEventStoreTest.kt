package app.kinjar.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.kinjar.data.SyncImportException.Reason
import app.kinjar.data.db.KinjarDb
import app.kinjar.domain.Account
import app.kinjar.domain.AccountCreated
import app.kinjar.domain.AccountUpdated
import app.kinjar.domain.Currency
import app.kinjar.domain.Event
import app.kinjar.domain.EventJson
import app.kinjar.domain.EventRecord
import app.kinjar.domain.Loan
import app.kinjar.domain.LoanCreated
import app.kinjar.domain.LoanPayment
import app.kinjar.domain.LoanPaymentAdded
import app.kinjar.domain.Money
import app.kinjar.domain.Receipt
import app.kinjar.domain.Transaction
import app.kinjar.domain.TransactionAdded
import app.kinjar.domain.TransactionDeleted
import app.kinjar.domain.TransactionEdited
import app.kinjar.domain.TransactionSource
import app.kinjar.domain.TransactionType
import app.kinjar.domain.billTxId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SyncEventStoreTest {
    private val clock = FixedClock(Instant.parse("2026-10-18T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val store = EventStore(db, "local", clock, Projector(db))
    private fun at(minute: Int) = clock.now().minusSeconds(3600).plusSeconds(minute * 60L).toEpochMilli()
    private fun rsd(value: String) = Money.of(value, Currency.RSD)
    private fun tx(id: String, amount: String = "100", source: TransactionSource = TransactionSource.MANUAL, account: String = "cash") =
        Transaction(id, TransactionType.EXPENSE, rsd(amount), account, null, null, LocalDate.of(2026, 10, 18), "", "", "m", source)

    private fun raw(id: String, device: String, seq: Long, minute: Int, event: Event): RawEventRecord {
        val payload = EventJson.encodeToString(Event.serializer(), event)
        return RawEventRecord(id, device, seq, at(minute), event::class.simpleName!!, payload)
    }

    private fun rejected(block: () -> Unit): Reason? = runCatching(block).exceptionOrNull()?.let { (it as SyncImportException).reason }

    private fun state(d: KinjarDb, s: EventStore) = listOf(
        d.txQueries.all().executeAsList().sortedBy { it.id }.map { listOf(it.id, it.amount, it.currency, it.account_id) },
        d.accountQueries.all().executeAsList().map { it.id to it.currency },
        d.loanPaymentQueries.all().executeAsList().map { it.id },
        s.syncIssues(),
    )

    private val cash = AccountCreated(Account("cash", "Cash", Currency.RSD))

    @Test fun `unknown types and fields export byte for byte and report as incompatible`() {
        val known = """{"type":"AccountCreated","account":{"id":"cash","name":"Cash","currency":"RSD"},"futureField":{"x":[1,2]}}"""
        val unknown = """{"type":"PocketMoneySet","amount":"5.00"}"""
        val records = listOf(
            RawEventRecord("e1", "peer", 1, at(1), "AccountCreated", known),
            RawEventRecord("e2", "peer", 2, at(2), "PocketMoneySet", unknown),
        )
        assertEquals(2, store.importRaw(records))
        assertEquals(records, store.exportRaw())
        assertEquals("Cash", db.accountQueries.byId("cash").executeAsOne().name)
        assertEquals(listOf(SyncIssue(SyncIssueKind.INCOMPATIBLE_EVENT, null, "e2")), store.syncIssues())

        val copy = testDb()
        EventStore(copy, "other", clock, Projector(copy)).importRaw(store.exportRaw().reversed())
        assertEquals(records, EventStore(copy, "other", clock, Projector(copy)).exportRaw())
    }

    @Test fun `repeated delivery is idempotent and local appends keep their own sequence`() = runTest {
        store.append(cash)
        val remote = listOf(raw("r1", "peer", 1, 1, TransactionAdded(tx("t1"))))
        assertEquals(1, store.importRaw(remote + remote))
        assertEquals(0, store.importRaw(remote))
        assertEquals(0, store.importRaw(store.exportRaw()))
        assertEquals(2L, store.append(TransactionAdded(tx("t2"))).seq)
        assertEquals(3L, db.eventQueries.count().executeAsOne())
    }

    @Test fun `identity conflicts against the log or inside the batch reject everything`() {
        val first = raw("r1", "peer", 1, 1, cash)
        store.importRaw(listOf(first))
        val before = store.exportRaw()
        val fresh = raw("r2", "peer", 2, 2, TransactionAdded(tx("t1")))
        assertEquals(Reason.IDENTITY_CONFLICT, rejected { store.importRaw(listOf(fresh, first.copy(payload = first.payload + " "))) })
        assertEquals(Reason.IDENTITY_CONFLICT, rejected { store.importRaw(listOf(fresh, first.copy(id = "other"))) })
        assertEquals(Reason.IDENTITY_CONFLICT, rejected { store.importRaw(listOf(fresh, fresh.copy(seq = 3), raw("r3", "peer", 3, 3, cash))) })
        assertEquals(Reason.IDENTITY_CONFLICT, rejected { store.importRaw(listOf(fresh, fresh.copy(occurredAt = fresh.occurredAt + 1))) })
        assertEquals(before, store.exportRaw())
        assertTrue(db.txQueries.all().executeAsList().isEmpty())
    }

    @Test fun `malformed or out of bounds records roll back the whole file`() = runTest {
        val good = raw("r1", "peer", 1, 1, TransactionAdded(tx("t1")))
        val receipt = Receipt("https://suf.purs.gov.rs/v/?vl=x", "1", "INV", "Shop", LocalDate.of(2026, 10, 18), rsd("100"), emptyList())
        val bad = listOf(
            good.copy(id = "r2", seq = 0),
            good.copy(id = "r2", seq = 2, occurredAt = clock.now().toEpochMilli() + EventStore.MAX_FUTURE_MILLIS + 1),
            good.copy(id = "r2", seq = 2, occurredAt = -1),
            good.copy(id = "r2", seq = 2, type = "TransactionDeleted"),
            good.copy(id = "r2", seq = 2, payload = "{not json"),
            good.copy(id = "r2", seq = 2, payload = "[1]"),
            good.copy(id = "", seq = 2),
            good.copy(id = "r 2", seq = 2),
            good.copy(id = "r2", seq = 2, payload = "x".repeat(EventStore.MAX_PAYLOAD_LENGTH + 1)),
            raw("r2", "peer", 2, 2, TransactionAdded(tx("wrong", source = TransactionSource.RECEIPT), receipt)),
        )
        bad.forEach { assertEquals(Reason.INVALID_RECORD, rejected { store.importRaw(listOf(good, it)) }) }
        assertTrue(store.exportRaw().isEmpty())
        assertTrue(db.txQueries.all().executeAsList().isEmpty())
        assertTrue(runCatching { store.append(TransactionAdded(tx("wrong", source = TransactionSource.RECEIPT), receipt)) }.isFailure)
        assertTrue(store.exportRaw().isEmpty())
    }

    @Test fun `an invalid receipt already in the log is skipped and reported instead of breaking rebuild`() {
        val receipt = Receipt("https://suf.purs.gov.rs/v/?vl=x", "1", "INV", "Shop", LocalDate.of(2026, 10, 18), rsd("100"), emptyList())
        val row = raw("bad", "old", 1, 1, TransactionAdded(tx("wrong", source = TransactionSource.RECEIPT), receipt))
        db.eventQueries.insert(row.id, row.deviceId, row.seq, row.occurredAt, row.type, row.payload)
        store.rebuild()
        assertTrue(db.txQueries.all().executeAsList().isEmpty())
        assertTrue(db.receiptQueries.byId(receipt.id).executeAsOneOrNull() == null)
        assertEquals(listOf(SyncIssue(SyncIssueKind.INVALID_EVENT, "wrong", "bad")), store.syncIssues())
    }

    @Test fun `deleted transactions stay deleted but a reopened bill can be paid again`() = runTest {
        store.append(cash)
        store.append(TransactionAdded(tx("t1")))
        store.append(TransactionDeleted("t1"))
        store.importRaw(listOf(raw("late", "peer", 1, 120, TransactionAdded(tx("t1", "999")))))
        assertTrue(db.txQueries.all().executeAsList().isEmpty())

        val bill = tx(billTxId("rent", "2026-10"), source = TransactionSource.RECURRING)
        store.append(TransactionAdded(bill))
        store.append(TransactionDeleted(bill.id))
        store.append(TransactionAdded(bill.copy(money = rsd("120"))))
        store.rebuild()
        assertEquals(listOf(bill.id to "120.00"), db.txQueries.all().executeAsList().map { it.id to it.amount })
        assertTrue(store.syncIssues().isEmpty())
    }

    @Test fun `concurrent financial changes converge with deterministic issues in every delivery order`() {
        val bill = billTxId("rent", "2026-10")
        val loan = Loan("loan", "Car", rsd("10000"), LocalDate.of(2026, 10, 1))
        val records = listOf(
            raw("a1", "a", 1, 1, cash),
            raw("a2", "a", 2, 2, AccountCreated(Account("eur", "Euro", Currency.RSD))),
            raw("a3", "a", 3, 3, LoanCreated(loan)),
            raw("a4", "a", 4, 4, LoanCreated(loan.copy(id = "loan2"))),
            raw("a5", "a", 5, 5, TransactionAdded(tx("shared"))),
            raw("b1", "b", 1, 6, TransactionAdded(tx(bill, "40000", TransactionSource.RECURRING))),
            raw("a6", "a", 6, 7, TransactionAdded(tx(bill, "40500", TransactionSource.RECURRING))),
            raw("b2", "b", 2, 8, LoanPaymentAdded(LoanPayment("pb", "loan", "shared", rsd("100"), null))),
            raw("a7", "a", 7, 9, LoanPaymentAdded(LoanPayment("pa", "loan2", "shared", rsd("100"), null))),
            raw("b3", "b", 3, 10, TransactionAdded(tx("rsd-on-eur", account = "eur"))),
            raw("a8", "a", 8, 11, AccountUpdated(Account("eur", "Euro", Currency.EUR))),
            raw("a9", "a", 9, 12, AccountUpdated(Account("cash", "Cash", Currency.RSD))),
        )
        val expected = listOf(
            SyncIssue(SyncIssueKind.TRANSACTION_CONFLICT, bill, "a6"),
            SyncIssue(SyncIssueKind.LOAN_CONFLICT, "shared", "a7"),
            SyncIssue(SyncIssueKind.ACCOUNT_CURRENCY_CONFLICT, "eur", "a8"),
        )
        val base = testDb()
        val baseStore = EventStore(base, "x", clock, Projector(base))
        baseStore.importRaw(records)
        assertEquals(expected, baseStore.syncIssues())
        assertEquals("RSD", base.accountQueries.byId("eur").executeAsOne().currency)
        assertEquals(listOf("pb"), base.loanPaymentQueries.all().executeAsList().map { it.id })
        assertEquals(listOf("40000.00"), base.txQueries.all().executeAsList().filter { it.id == bill }.map { it.amount })
        val baseline = state(base, baseStore)
        for (seed in 1..5L) {
            val d = testDb()
            val s = EventStore(d, "y", clock, Projector(d))
            records.shuffled(java.util.Random(seed)).chunked(3).forEach { chunk -> s.importRaw(chunk); s.importRaw(chunk) }
            assertEquals(baseline, state(d, s))
        }

        val d = testDb()
        val s = EventStore(d, "z", clock, Projector(d))
        s.importRaw(records + raw("a10", "a", 10, 13, TransactionEdited(tx(bill, "40500", TransactionSource.RECURRING))))
        assertEquals(expected.drop(1), s.syncIssues())
    }

    @Test fun `currency change ordered before an old currency entry is reported as a mismatch`() {
        store.importRaw(listOf(
            raw("a1", "a", 1, 1, AccountCreated(Account("acc", "Acc", Currency.RSD))),
            raw("a2", "a", 2, 2, AccountUpdated(Account("acc", "Acc", Currency.EUR))),
            raw("b1", "b", 1, 3, TransactionAdded(tx("rsd", account = "acc"))),
        ))
        assertEquals("EUR", db.accountQueries.byId("acc").executeAsOne().currency)
        assertEquals(1, db.txQueries.all().executeAsList().size)
        assertEquals(listOf(SyncIssue(SyncIssueKind.CURRENCY_MISMATCH, "rsd", null)), store.syncIssues())
    }

    @Test fun `replacement is atomic and refuses a log that changed after confirmation`() = runTest {
        store.append(cash)
        val confirmed = store.exportRaw()
        val incoming = listOf(raw("s1", "source", 1, 1, AccountCreated(Account("card", "Card", Currency.RSD))))
        store.append(TransactionAdded(tx("late")))
        val changed = store.exportRaw()
        assertEquals(Reason.STALE_REPLACEMENT, rejected { store.replaceRaw(incoming, confirmed) })
        assertEquals(Reason.INVALID_RECORD, rejected { store.replaceRaw(incoming + incoming[0].copy(id = "s2", seq = 0), changed) })
        assertEquals(changed, store.exportRaw())
        store.replaceRaw(incoming, changed)
        assertEquals(incoming, store.exportRaw())
        assertEquals(listOf("card"), db.accountQueries.all().executeAsList().map { it.id })
        assertTrue(db.txQueries.all().executeAsList().isEmpty())
    }

    @Test fun `typed adapter still applies remote records`() {
        store.applyRemote(listOf(EventRecord("r1", "peer", 1, clock.now(), cash)))
        assertEquals(listOf("cash"), db.accountQueries.all().executeAsList().map { it.id })
    }
}
