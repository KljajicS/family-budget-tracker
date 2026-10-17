package rs.budget.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import rs.budget.data.db.BudgetDb
import rs.budget.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LoanProjectionTest {
    private val clock = FixedClock(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private fun eur(value: String) = Money.of(value, Currency.EUR)

    @Test fun `removed payment cannot resurrect and a new link can reuse transaction`() = runTest {
        val db = testDb()
        val store = EventStore(db, "d", clock, Projector(db))
        val loan = Loan("l", "Car", eur("6800"), LocalDate.of(2026, 10, 17))
        val p = LoanPayment("p", "l", "t", eur("250"), eur("200"))
        store.appendAll(listOf(LoanCreated(loan), LoanPaymentAdded(p), LoanPaymentRemoved(p.id), LoanPaymentUpdated(p.copy(principal = eur("100"))), LoanPaymentAdded(p)))
        assertTrue(db.loanPaymentQueries.all().executeAsList().isEmpty())
        store.append(LoanPaymentAdded(p.copy(id = "p2")))
        store.append(LoanArchived(loan.id))
        store.append(LoanUpdated(loan.copy(name = "Updated")))
        val before = db.loanPaymentQueries.all().executeAsList()
        store.rebuild()
        assertEquals(before, db.loanPaymentQueries.all().executeAsList())
        assertEquals(1L, db.loanQueries.byId(loan.id).executeAsOne().archived)
    }

    @Test fun `schema seven migration preserves accounts and events`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        javaClass.getResource("/schema-v5.sql")!!.readText().split(';').filter { it.isNotBlank() }.forEach { driver.execute(null, it, 0) }
        BudgetDb.Schema.migrate(driver, 5, 7)
        driver.execute(null, "INSERT INTO account(id,name,currency,kind,archived,position) VALUES ('a','Cash','RSD','SPENDING',0,0)", 0)
        driver.execute(null, "INSERT INTO event VALUES ('e','d',1,1,'future','{}')", 0)
        BudgetDb.Schema.migrate(driver, 7, BudgetDb.Schema.version)
        val db = BudgetDb(driver)
        assertEquals("Cash", db.accountQueries.byId("a").executeAsOne().name)
        assertEquals(1L, db.eventQueries.count().executeAsOne())
        assertTrue(db.loanQueries.all().executeAsList().isEmpty())
    }
}
