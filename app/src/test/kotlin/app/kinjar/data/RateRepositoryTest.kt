package app.kinjar.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import app.kinjar.data.db.KinjarDb
import app.kinjar.data.rates.RateRepository
import app.kinjar.domain.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RateRepositoryTest {
    private val clock = FixedClock(Instant.parse("2026-10-15T10:00:00Z"), ZoneId.of("UTC"))
    private val day = LocalDate.of(2026, 10, 15)
    private val quotes = mapOf(Currency.EUR to BigDecimal("117.3931"), Currency.USD to BigDecimal("104.6564"))

    @Test fun `override reset and reapply replay deterministically without clearing public cache`() = runTest {
        val records = listOf("120", null, "118").mapIndexed { index, rate ->
            EventRecord("event$index", "remote", index.toLong(), clock.now().plusSeconds(index.toLong()), ExchangeRateOverrideSet(Currency.EUR, rate))
        }
        for (order in listOf(records, records.reversed())) {
            val db = testDb()
            val store = EventStore(db, "local", clock, Projector(db))
            val repo = RateRepository(db, store, clock)
            repo.cache(quotes, day)
            order.forEach { store.applyRemote(listOf(it)) }
            store.rebuild()
            assertEquals(BigDecimal("118"), repo.snapshots.first().rates.rate(Currency.EUR))
            repo.setOverride(Currency.EUR, null)
            assertEquals(quotes[Currency.EUR], repo.snapshots.first().rates.rate(Currency.EUR))
            assertEquals(2, repo.snapshots.first().cached.size)
        }
    }

    @Test fun `invalid override and cache leave saved rates and events untouched`() = runTest {
        val db = testDb()
        val store = EventStore(db, "local", clock, Projector(db))
        val repo = RateRepository(db, store, clock)
        repo.cache(quotes, day)
        repo.setOverride(Currency.EUR, "119")
        val before = repo.snapshots.first()
        for (invalid in listOf("0", "-1", "1e100000", "1000001")) {
            assertTrue(runCatching { repo.setOverride(Currency.EUR, invalid) }.isFailure)
        }
        assertTrue(runCatching { repo.cache(quotes, day.minusDays(1)) }.isFailure)
        assertTrue(runCatching { repo.cache(quotes, day.plusDays(1)) }.isFailure)
        assertTrue(runCatching { repo.cache(quotes - Currency.USD, day) }.isFailure)
        assertTrue(runCatching {
            store.append(ExchangeRateOverrideSet(Currency.EUR, "121")) { error("write failed") }
        }.isFailure)
        assertEquals(before, repo.snapshots.first())
        assertEquals(1L, db.eventQueries.count().executeAsOne())
    }

    @Test fun `schema five upgrade preserves every existing table and adds empty rate tables`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        javaClass.getResource("/schema-v5.sql")!!.readText().split(';').filter { it.isNotBlank() }
            .forEach { driver.execute(null, it, 0) }
        val inserts = listOf(
            "INSERT INTO account VALUES ('a','Cash','RSD','SPENDING',0)",
            "INSERT INTO member VALUES ('m','Name','device')",
            "INSERT INTO category VALUES ('c','Food','#ffffff',0)",
            "INSERT INTO event VALUES ('e','device',1,1,'unknown','{}')",
            "INSERT INTO tx VALUES ('t','EXPENSE','10.00','RSD','a',NULL,'c','2026-10-15','','','m','MANUAL',1)",
            "INSERT INTO budget VALUES ('c','2026-10','100.00','RSD')",
            "INSERT INTO recurring_rule VALUES ('r','Bill','10.00','RSD','a','c','MONTHLY','2026-10-15',0)",
            "INSERT INTO bill_skip VALUES ('r','2026-10')",
            "INSERT INTO goal VALUES ('g','Goal','100.00','10.00','RSD',0)",
            "INSERT INTO goal_allocation VALUES ('ga','g','10.00','RSD','2026-10-15')",
            "INSERT INTO receipt VALUES ('rc','t','issuer','invoice','url','merchant','2026-10-15','10.00','[]')",
        )
        inserts.forEach { driver.execute(null, it, 0) }
        KinjarDb.Schema.migrate(driver, 5, KinjarDb.Schema.version)
        val db = KinjarDb(driver)
        assertEquals("10.00", db.txQueries.all().executeAsList().single().amount)
        assertEquals(1L, db.eventQueries.count().executeAsOne())
        assertEquals(1, db.accountQueries.all().executeAsList().size)
        assertEquals(1, db.memberQueries.all().executeAsList().size)
        assertEquals(1, db.categoryQueries.all().executeAsList().size)
        assertEquals(1, db.budgetQueries.all().executeAsList().size)
        assertEquals(1, db.recurringRuleQueries.active().executeAsList().size)
        assertEquals(1, db.billSkipQueries.all().executeAsList().size)
        assertEquals(1, db.goalQueries.active().executeAsList().size)
        assertEquals(1, db.goalAllocationQueries.all().executeAsList().size)
        assertNotNull(db.receiptQueries.byId("rc").executeAsOneOrNull())
        assertTrue(db.exchangeRateQueries.all().executeAsList().isEmpty())
        assertTrue(db.exchangeRateOverrideQueries.all().executeAsList().isEmpty())
        driver.close()
    }
}
