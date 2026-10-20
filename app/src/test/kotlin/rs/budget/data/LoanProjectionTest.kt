package rs.budget.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
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

    @Test fun `schema seven migration preserves financial history through replay`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        javaClass.getResource("/schema-v5.sql")!!.readText().split(';').filter { it.isNotBlank() }.forEach { driver.execute(null, it, 0) }
        BudgetDb.Schema.migrate(driver, 5, 7)
        val oldDb = BudgetDb(driver)
        fun rsd(value: String) = Money.of(value, Currency.RSD)
        val income = Transaction("income", TransactionType.INCOME, rsd("1000"), "a", null, null, clock.today(), "", "", "m", TransactionSource.MANUAL)
        val events = listOf(AccountCreated(Account("a", "Cash", Currency.RSD)), TransactionAdded(income),
            TransactionAdded(income.copy(id = "expense", type = TransactionType.EXPENSE, money = rsd("250"))),
            GoalSet(Goal("g", "Reserve", rsd("1000"))),
            RecurringRuleSet(RecurringRule("r", "Bill", rsd("100"), "a", "c", Cadence.MONTHLY, clock.today())))
        // Written as rows: today's projector needs tables that schema seven does not have yet.
        events.forEachIndexed { i, e ->
            val payload = EventJson.encodeToString(Event.serializer(), e)
            oldDb.eventQueries.insert("e$i", "old", i + 1L, clock.now().toEpochMilli() + i, e::class.simpleName!!, payload)
        }
        oldDb.accountQueries.insertIgnore("a", "Cash", "RSD", "SPENDING", 0)
        BudgetDb.Schema.migrate(driver, 7, BudgetDb.Schema.version)
        val db = BudgetDb(driver)
        assertEquals("Cash", db.accountQueries.byId("a").executeAsOne().name)
        assertEquals(events.size.toLong(), db.eventQueries.count().executeAsOne())
        assertTrue(db.loanQueries.all().executeAsList().isEmpty())
        val store = EventStore(db, "new", clock, Projector(db))
        val repo = BudgetRepository(db, store, clock)
        store.rebuild()
        val after = repo.financial.first()
        assertEquals(setOf("income", "expense"), after.transactions.map { it.id }.toSet())
        assertTrue(store.syncIssues().isEmpty())
        assertEquals(rsd("750"), after.home.savingsRsd)
        assertEquals(rsd("250"), after.home.spent)
        assertEquals(rsd("750"), after.goals.single().funded)
        assertEquals("r", after.bills.single().rule.id)
    }
}
