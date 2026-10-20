package app.kinjar.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import app.kinjar.data.db.KinjarDb
import app.kinjar.domain.Account
import app.kinjar.domain.AccountArchived
import app.kinjar.domain.AccountCreated
import app.kinjar.domain.AccountKind
import app.kinjar.domain.AccountsReordered
import app.kinjar.domain.Budget
import app.kinjar.domain.BudgetSet
import app.kinjar.domain.Currency
import app.kinjar.domain.Event
import app.kinjar.domain.EventJson
import app.kinjar.domain.EventRecord
import app.kinjar.domain.Goal
import app.kinjar.domain.GoalAllocated
import app.kinjar.domain.GoalAllocation
import app.kinjar.domain.GoalSet
import app.kinjar.domain.GoalsReordered
import app.kinjar.domain.Money
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class OrderingTest {
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private fun at(minute: Int) = Instant.parse("2026-10-14T10:00:00Z").plusSeconds(minute * 60L)
    private fun rsd(s: String) = Money.of(s, Currency.RSD)
    private fun account(id: String, name: String) = Account(id, name, Currency.RSD, AccountKind.SAVINGS)

    @Test
    fun `schema six upgrade keeps legacy rows, then a rebuild orders by creation and keeps old budgets and allocations`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        javaClass.getResource("/schema-v5.sql")!!.readText().split(';').filter { it.isNotBlank() }.forEach { driver.execute(null, it, 0) }
        KinjarDb.Schema.migrate(driver, 5, 6)
        val events: List<Event> = listOf(
            AccountCreated(account("z", "Zeta")),
            AccountCreated(account("a", "Alpha")),
            GoalSet(Goal("g2", "Second", rsd("100"), rsd("10"))),
            GoalSet(Goal("g1", "First", rsd("100"), rsd("10"))),
            BudgetSet(Budget("c", YearMonth.of(2026, 10), rsd("500"))),
            GoalAllocated(GoalAllocation("al", "g1", rsd("40"), LocalDate.of(2026, 10, 1))),
        )
        events.forEachIndexed { i, e ->
            val payload = EventJson.encodeToString(Event.serializer(), e)
            driver.execute(null, "INSERT INTO event VALUES ('e$i','old',${i + 1},${at(i).toEpochMilli()},'x','${payload.replace("'", "''")}')", 0)
        }
        listOf(
            "INSERT INTO account VALUES ('z','Zeta','RSD','SAVINGS',0)",
            "INSERT INTO account VALUES ('a','Alpha','RSD','SAVINGS',0)",
            "INSERT INTO goal VALUES ('g2','Second','100.00','10.00','RSD',0)",
            "INSERT INTO goal VALUES ('g1','First','100.00','10.00','RSD',0)",
            "INSERT INTO budget VALUES ('c','2026-10','500.00','RSD')",
            "INSERT INTO goal_allocation VALUES ('al','g1','40.00','RSD','2026-10-01')",
        ).forEach { driver.execute(null, it, 0) }

        KinjarDb.Schema.migrate(driver, 6, KinjarDb.Schema.version)
        val db = KinjarDb(driver)
        val repo = BudgetRepository(db, EventStore(db, "dev", clock, Projector(db)), clock)
        assertNull(db.accountQueries.byId("a").executeAsOne().position)
        assertEquals(listOf("a", "z"), repo.accounts.first().map { it.id })
        assertEquals(listOf("g1", "g2"), repo.goals.first().map { it.id })

        EventStore(db, "dev", clock, Projector(db)).rebuild()
        assertEquals(listOf("z", "a"), repo.accounts.first().map { it.id })
        assertEquals(listOf("g2", "g1"), repo.goals.first().map { it.id })
        assertEquals(AccountKind.SAVINGS, repo.accounts.first().first().kind)
        assertEquals(1, db.budgetQueries.all().executeAsList().size)
        assertEquals("40.00", db.goalAllocationQueries.all().executeAsList().single().amount)
        assertEquals(rsd("10"), repo.goal("g1")!!.monthly)
        driver.close()
    }

    @Test
    fun `new entities append after rows omitted from a partial reorder`() = runTest {
        val db = testDb()
        val store = EventStore(db, "dev", clock, Projector(db))
        for (id in listOf("a", "b", "c")) {
            store.append(AccountCreated(account(id, id)))
            store.append(GoalSet(Goal(id, id, rsd("10"))))
        }
        store.append(AccountsReordered(listOf("b")))
        store.append(GoalsReordered(listOf("b")))
        store.append(AccountCreated(account("d", "d")))
        store.append(GoalSet(Goal("d", "d", rsd("10"))))
        repeat(2) {
            assertEquals(listOf("b", "a", "c", "d"), db.accountQueries.active().executeAsList().map { it.id })
            assertEquals(listOf("b", "a", "c", "d"), db.goalQueries.active().executeAsList().map { it.id })
            store.rebuild()
        }
    }

    @Test
    fun `reorders converge across devices, unknown and newer rows append, archive keeps the rest in order`() {
        val events: List<Pair<String, Event>> = listOf(
            "a" to AccountCreated(account("cash", "Cash")),
            "a" to AccountCreated(account("card", "Card")),
            "b" to AccountCreated(account("eur", "Euro")),
            "a" to AccountsReordered(listOf("card", "cash")),
            "a" to AccountCreated(account("new", "New")),
            "b" to AccountsReordered(listOf("eur", "card")),
            "b" to AccountArchived("card"),
            "a" to GoalSet(Goal("g1", "One", rsd("1"))),
            "b" to GoalSet(Goal("g2", "Two", rsd("1"))),
            "a" to GoalsReordered(listOf("g2", "g1")),
        )
        val seqs = mutableMapOf<String, Long>()
        val records = events.mapIndexed { i, (device, e) -> EventRecord("e$i", device, seqs.merge(device, 1L, Long::plus)!!, at(i), e) }
        fun order(db: KinjarDb) = db.accountQueries.active().executeAsList().map { it.id } to db.goalQueries.active().executeAsList().map { it.id }

        val inOrder = testDb().also { EventStore(it, "x", clock, Projector(it)).applyRemote(records) }
        val shuffled = testDb().also { d ->
            val store = EventStore(d, "y", clock, Projector(d))
            records.shuffled(java.util.Random(3)).chunked(2).forEach(store::applyRemote)
        }
        assertEquals(order(inOrder), order(shuffled))
        assertEquals(listOf("eur", "cash", "new") to listOf("g2", "g1"), order(inOrder))
    }
}
