package rs.budget.ui.goals

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.testDb
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.domain.Goal
import rs.budget.domain.GoalAllocation
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
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun rsd(s: String) = Money.of(s, Currency.RSD)

    @Test
    fun `totals, unassigned, needs line and card order`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING), Account("sav", "Savings", Currency.RSD, AccountKind.SAVINGS)))
        val m = repo.currentMember()!!.id
        repo.add(Transaction("t1", TransactionType.INCOME, rsd("200000"), "cash", null, null, LocalDate.of(2026, 8, 1), "", "", m, TransactionSource.MANUAL))
        repo.add(Transaction("t2", TransactionType.EXPENSE, rsd("150000"), "cash", null, "cat-other", LocalDate.of(2026, 8, 2), "", "", m, TransactionSource.MANUAL))
        repo.add(Transaction("t3", TransactionType.TRANSFER, rsd("40000"), "cash", "sav", null, LocalDate.of(2026, 9, 1), "", "", m, TransactionSource.MANUAL))
        repo.setGoal(Goal("fund", "Fund", rsd("100000"), rsd("20000")))
        repo.setGoal(Goal("trip", "Trip", rsd("50000"), rsd("5000")))
        repo.allocate(GoalAllocation("a1", "trip", rsd("25000"), LocalDate.of(2026, 10, 1)))

        val s = GoalsViewModel(repo, clock).state.filterNotNull().first { it.statuses.size == 2 }
        assertEquals(rsd("40000"), s.savingsTotal)
        assertEquals(rsd("40000"), s.savingsByCurrency[Currency.RSD])
        assertEquals(rsd("15000"), s.unassigned)
        assertEquals(rsd("25000"), s.needs)
        // Completed months in the window: Aug +50000, Sep 0, others 0, so 10000 on average: short by 15000.
        assertEquals(rsd("10000"), s.averageSaved)
        assertTrue((s.needs - s.averageSaved).amount.signum() > 0)
        assertEquals(listOf("trip", "fund"), s.statuses.map { it.goal.id })

        // Archive the big goal: needs drop to 5000, below the 10000 average, so the line reads as covered.
        repo.archiveGoal("fund")
        val covered = GoalsViewModel(repo, clock).state.filterNotNull().first { it.statuses.size == 1 }
        assertEquals(rsd("5000"), covered.needs)
        assertTrue((covered.needs - covered.averageSaved).amount.signum() < 0)
        assertEquals(rsd("15000"), covered.unassigned)
    }
}
