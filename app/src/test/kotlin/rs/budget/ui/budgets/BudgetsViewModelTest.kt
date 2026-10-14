package rs.budget.ui.budgets

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
import rs.budget.data.testDb
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.domain.Money
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `edit prefills carried-forward amounts and save writes the current month`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        repo.setBudgets(YearMonth.of(2026, 9), mapOf("cat-groceries" to Money.of("50000", Currency.RSD)))
        val edit = EditBudgetsViewModel(repo, clock)
        val rows = edit.state.first { it.rows.isNotEmpty() }.rows
        assertEquals("50000", rows.first { it.category.id == "cat-groceries" }.amountText)
        assertEquals("", rows.first { it.category.id == "cat-fun" }.amountText)
        edit.setAmount("cat-fun", "5000")
        edit.setAmount("cat-groceries", "60000")
        edit.save()
        edit.state.first { it.saved }
        val vm = BudgetsViewModel(repo, clock)
        val s = vm.state.filterNotNull().first { it.statuses.size == 2 }
        assertEquals(YearMonth.of(2026, 10), s.month)
        assertEquals(18, s.daysLeft)
        assertEquals(Money.of("65000", Currency.RSD), s.total)
        assertEquals(Money.of("50000", Currency.RSD), repo.effectiveBudgets(YearMonth.of(2026, 9)).first().single().money)
    }
}
