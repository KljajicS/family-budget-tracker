package rs.budget.ui.goals

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.db.BudgetDb
import rs.budget.domain.Account
import rs.budget.domain.Currency
import rs.budget.domain.Goal
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db = BudgetDb(driver).also { BudgetDb.Schema.create(driver) }
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    private val models = mutableListOf<GoalsViewModel>()
    @After fun tearDown() {
        runTest(dispatcher) { models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private fun rsd(s: String) = Money.of(s, Currency.RSD)

    @Test
    fun `goals fill from total savings in priority order and reordering refills them`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD), Account("sav", "Savings", Currency.RSD)))
        val m = repo.currentMember()!!.id
        repo.add(Transaction("t1", TransactionType.INCOME, rsd("100000"), "cash", null, null, LocalDate.of(2026, 10, 1), "", "", m, TransactionSource.MANUAL))
        repo.add(Transaction("t2", TransactionType.TRANSFER, rsd("40000"), "cash", "sav", null, LocalDate.of(2026, 10, 2), "", "", m, TransactionSource.MANUAL))
        repo.setGoal(Goal("fund", "Fund", rsd("80000")))
        repo.setGoal(Goal("trip", "Trip", rsd("50000")))

        val vm = GoalsViewModel(repo, clock).also { models += it }
        val s = vm.state.filterNotNull().first { it.goals.size == 2 }
        assertEquals(listOf("fund", "trip"), s.goals.map { it.goal.id })
        assertEquals(listOf(rsd("80000"), rsd("20000")), s.goals.map { it.funded })

        vm.reorder(listOf("trip", "fund"))
        val swapped = vm.state.filterNotNull().first { it.goals.first().goal.id == "trip" }
        assertEquals(listOf(rsd("50000"), rsd("50000")), swapped.goals.map { it.funded })
    }

    @Test
    fun `failed reorder keeps stored order and bumps the reset key`() = runTest(dispatcher) {
        repo.setGoal(Goal("a", "A", rsd("1")))
        repo.setGoal(Goal("b", "B", rsd("1")))
        val vm = GoalsViewModel(repo, clock).also { models += it }
        vm.state.filterNotNull().first { it.goals.size == 2 }
        driver.execute(null, "CREATE TRIGGER fail_order BEFORE INSERT ON event BEGIN SELECT RAISE(ABORT, 'disk full'); END", 0)
        vm.reorder(listOf("b", "a"))
        vm.writes.state.first { it.failed }
        val s = vm.state.filterNotNull().first { it.reorderFailures == 1 }
        assertEquals(listOf("a", "b"), s.goals.map { it.goal.id })
    }
}
