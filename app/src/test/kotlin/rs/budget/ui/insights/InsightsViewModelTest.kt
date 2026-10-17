package rs.budget.ui.insights

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

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
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    private val models = mutableListOf<InsightsViewModel>()
    @After fun tearDown() {
        runTest(dispatcher) { models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    @Test
    fun `month arrows stop at the first transaction and at today, archived categories keep their name`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val m = repo.currentMember()!!.id
        repo.add(Transaction("t1", TransactionType.EXPENSE, Money.of("500", Currency.RSD), "cash", null, "cat-fun", LocalDate.of(2026, 8, 3), "", "", m, TransactionSource.MANUAL))
        repo.archiveCategory("cat-fun")

        val vm = InsightsViewModel(repo, clock).also { models += it }
        var s = vm.state.filterNotNull().first { it.hasData }
        assertEquals(YearMonth.of(2026, 10), s.summary.month)
        assertTrue(s.canGoBack)
        assertFalse(s.canGoForward)
        assertEquals("Fun", s.categories["cat-fun"]?.name)

        vm.previousMonth()
        vm.previousMonth()
        vm.previousMonth()
        s = vm.state.filterNotNull().first { it.summary.month == YearMonth.of(2026, 8) }
        assertFalse(s.canGoBack)
        assertEquals(listOf("cat-fun"), s.summary.slices.map { it.categoryId })

        vm.select(s.summary.slices.single())
        s = vm.state.filterNotNull().first { it.selected != null }
        assertEquals(listOf("t1"), s.drilldown.map { it.id })
        assertEquals("Cash", s.accountNames["cash"])
        vm.select(s.summary.slices.single())
        vm.state.filterNotNull().first { it.selected == null }
        vm.select(s.summary.slices.single())
        vm.state.filterNotNull().first { it.selected != null }

        vm.nextMonth()
        vm.nextMonth()
        vm.nextMonth()
        s = vm.state.filterNotNull().first { it.summary.month == YearMonth.of(2026, 10) }
        assertFalse(s.canGoForward)
        assertEquals(null, s.selected)
    }
}
