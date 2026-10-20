package app.kinjar.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import app.kinjar.data.db.KinjarDb
import app.kinjar.domain.*
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SharedTransactionsTest {
    @Test fun `subscribers share database listener and reload after subscription expires`() = runTest {
        val sqlite = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KinjarDb.Schema.create(sqlite)
        val added = AtomicInteger()
        val removed = AtomicInteger()
        val driver = object : SqlDriver by sqlite {
            override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
                if ("tx" in queryKeys) added.incrementAndGet()
                sqlite.addListener(*queryKeys, listener = listener)
            }
            override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
                if ("tx" in queryKeys) removed.incrementAndGet()
                sqlite.removeListener(*queryKeys, listener = listener)
            }
        }
        val db = KinjarDb(driver)
        val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
        val repo = BudgetRepository(db, EventStore(db, "device", clock, Projector(db)), clock, sharingScope = backgroundScope)
        val account = Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)
        repo.setUp("S", listOf(account))
        val first = Channel<List<Transaction>>(Channel.UNLIMITED)
        val second = Channel<List<Transaction>>(Channel.UNLIMITED)
        val a = backgroundScope.launch { repo.transactions.collect { first.send(it) } }
        val b = backgroundScope.launch { repo.transactions.collect { second.send(it) } }
        assertTrue(first.receive().isEmpty())
        assertTrue(second.receive().isEmpty())
        assertEquals(1, added.get())
        val tx = Transaction("t", TransactionType.INCOME, Money.of("100", Currency.RSD), "cash", null, null,
            clock.today(), "", "", repo.currentMember()!!.id, TransactionSource.MANUAL)
        repo.add(tx)
        assertEquals(listOf(tx), first.receive())
        assertEquals(listOf(tx), second.receive())
        assertTrue(repo.accountInUse("cash"))
        assertTrue(runCatching { repo.updateAccount(account.copy(currency = Currency.EUR)) }.isFailure)
        a.cancelAndJoin()
        b.cancelAndJoin()
        withContext(Dispatchers.Default) { delay(5_500) }
        repo.add(tx.copy(id = "t2"))
        assertEquals(2, repo.transactions.first().size)
        assertEquals(2, added.get())
        assertEquals(1, removed.get())
    }
}
