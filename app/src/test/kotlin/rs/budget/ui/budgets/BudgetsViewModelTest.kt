package rs.budget.ui.budgets

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertFalse
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
import rs.budget.domain.Budget
import rs.budget.domain.Cadence
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.RecurringRule
import java.time.Instant
import java.time.LocalDate
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
    fun `create a budget with its own currency, then edit prefills and delete removes it`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        repo.setBudget(Budget("cat-groceries", YearMonth.of(2026, 9), Money.of("50000", Currency.RSD)))

        val create = EditBudgetViewModel(repo, clock, SavedStateHandle())
        create.state.first { it.categories.isNotEmpty() }
        assertFalse(create.state.value.canSave)
        assertTrue("cat-groceries" in create.state.value.taken)
        create.setCategory("cat-fun")
        create.setAmount("50")
        create.setCurrency(Currency.EUR)
        assertTrue(create.state.value.canSave)
        create.save()
        create.state.first { it.done }

        val list = BudgetsViewModel(repo, clock).state.filterNotNull().first { it.statuses.size == 2 }
        assertEquals(YearMonth.of(2026, 10), list.month)
        assertEquals(18, list.daysLeft)

        val edit = EditBudgetViewModel(repo, clock, SavedStateHandle(mapOf("category" to "cat-fun")))
        val s = edit.state.first { it.categories.isNotEmpty() }
        assertTrue(s.editing)
        assertEquals("50", s.amountText)
        assertEquals(Currency.EUR, s.currency)
        edit.delete()
        edit.state.first { it.done }
        assertEquals(listOf("cat-groceries"), repo.effectiveBudgets(YearMonth.of(2026, 10)).first().map { it.categoryId })
        assertEquals(1, repo.effectiveBudgets(YearMonth.of(2026, 9)).first().size)
    }

    @Test
    fun `upcoming lists overdue first with an RSD total`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        repo.setRule(RecurringRule("net", "Internet", Money.of("3490", Currency.RSD), "cash", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 10, 25)))
        repo.setRule(RecurringRule("rent", "Rent", Money.of("40000", Currency.RSD), "cash", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 9, 5)))
        val s = BudgetsViewModel(repo, clock).state.filterNotNull().first { it.upcoming.size == 3 }
        assertEquals(listOf("Rent", "Rent", "Internet"), s.upcoming.map { it.rule.name })
        // Today is 14 Oct: the September rent and the 5 Oct rent are both past due.
        assertEquals(listOf(true, true, false), s.upcoming.map { it.overdue })
        assertEquals(Money.of("83490", Currency.RSD), s.upcomingTotal)
        assertEquals("Cash", s.accountNames["cash"])
    }
}
