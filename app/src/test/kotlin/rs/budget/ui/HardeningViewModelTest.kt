package rs.budget.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import rs.budget.RootViewModel
import rs.budget.data.*
import rs.budget.data.db.BudgetDb
import rs.budget.domain.*
import rs.budget.ui.budgets.EditState
import rs.budget.ui.add.AddViewModel
import rs.budget.ui.goals.EditGoalState
import rs.budget.ui.goals.EditGoalViewModel
import rs.budget.ui.goals.GoalDetailViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class HardeningViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db = BudgetDb(driver).also { BudgetDb.Schema.create(driver) }
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val repo = BudgetRepository(db, EventStore(db, "device", clock, Projector(db)), clock)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `money inputs reject exponents excess precision and unbounded values`() {
        for (text in listOf("1e2147483647", "1e-2147483647", "0.001", "12.345", "9".repeat(1000), "-5", "NaN")) {
            assertNull(parseMoneyInput(text, Currency.RSD))
            assertFalse(EditState(categoryId = "c", amountText = text).canSave)
            assertFalse(EditGoalState(name = "Trip", targetText = text).canSave)
        }
        assertFalse(EditState(categoryId = "c", amountText = "0").canSave)
        assertEquals(Money.of("12.34", Currency.RSD), parseMoneyInput("12.34", Currency.RSD))
        assertTrue(EditGoalState(name = "Trip", targetText = "100", monthlyText = "").canSave)
    }

    @Test fun `double tap creates one goal and completed form cannot resubmit`() = runTest(dispatcher) {
        val vm = EditGoalViewModel(repo, SavedStateHandle())
        vm.setName("Trip")
        vm.setTarget("1000")
        vm.save()
        vm.save()
        assertTrue(vm.writes.state.value.busy)
        vm.writes.state.first { it.finished }
        vm.save()
        assertEquals(1, db.goalQueries.active().executeAsList().size)
        assertEquals(1L, db.eventQueries.count().executeAsOne())
    }

    @Test fun `failed allocation preserves draft and retry records it once`() = runTest(dispatcher) {
        repo.setGoal(Goal("g", "Trip", Money.of("1000", Currency.RSD), Money.zero(Currency.RSD)))
        val vm = GoalDetailViewModel(repo, clock, SavedStateHandle(mapOf("id" to "g")))
        vm.setAmount("25")
        driver.execute(null, "CREATE TRIGGER fail_allocation BEFORE INSERT ON goal_allocation BEGIN SELECT RAISE(ABORT, 'disk full'); END", 0)
        vm.add()
        vm.add()
        vm.writes.state.first { it.failed }
        assertTrue(db.goalAllocationQueries.all().executeAsList().isEmpty())
        driver.execute(null, "DROP TRIGGER fail_allocation", 0)
        vm.add()
        vm.writes.state.first { !it.busy && !it.failed }
        assertEquals("25.00", db.goalAllocationQueries.all().executeAsList().single().amount)
    }

    @Test fun `startup initializes off caller thread and supports retry`() = runTest(dispatcher) {
        val caller = Thread.currentThread()
        val worker = AtomicReference<Thread>()
        var fail = true
        val vm = RootViewModel(dagger.Lazy {
            worker.set(Thread.currentThread())
            if (fail) error("Cannot open database")
            repo
        }, ThemeStore({ null }, {}))
        assertNull(vm.hasMember.value)
        vm.failed.first { it }
        assertNotEquals(caller, worker.get())
        fail = false
        vm.load()
        assertEquals(false, vm.hasMember.first { it != null })
        assertFalse(vm.failed.value)
    }

    @Test fun `failed bill skip retains sheet and retries without duplicate events`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        repo.setRule(RecurringRule("rent", "Rent", Money.of("100", Currency.RSD), "cash", "cat-bills", Cadence.MONTHLY, clock.today()))
        val vm = AddViewModel(repo, clock, SavedStateHandle(mapOf("rule" to "rent", "due" to clock.today().toString())))
        vm.state.first { it.bill != null && it.accounts.isNotEmpty() }
        driver.execute(null, "CREATE TRIGGER fail_skip BEFORE INSERT ON bill_skip BEGIN SELECT RAISE(ABORT, 'disk full'); END", 0)
        vm.skip()
        vm.skip()
        vm.state.first { it.saveError != null }
        assertFalse(vm.state.value.saved)
        assertFalse(vm.state.value.saving)
        assertTrue(db.billSkipQueries.all().executeAsList().isEmpty())
        driver.execute(null, "DROP TRIGGER fail_skip", 0)
        val before = db.eventQueries.count().executeAsOne()
        vm.skip()
        vm.state.first { it.saved }
        assertEquals(before + 1, db.eventQueries.count().executeAsOne())
        assertEquals(1, db.billSkipQueries.all().executeAsList().size)
    }
}
