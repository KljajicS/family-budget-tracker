package rs.budget.ui.home

import androidx.lifecycle.ViewModel
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    private val models = mutableListOf<ViewModel>()
    @After fun tearDown() {
        runTest(dispatcher) { models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    @Test
    fun `state reflects repository`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING), Account("card", "Card", Currency.RSD), Account("old", "Old", Currency.RSD)))
        val m = repo.currentMember()!!.id
        repo.add(Transaction("t1", TransactionType.INCOME, Money.of("1000", Currency.RSD), "cash", null, null, LocalDate.of(2026, 10, 1), "", "", m, TransactionSource.MANUAL))
        repo.add(Transaction("t2", TransactionType.EXPENSE, Money.of("250", Currency.RSD), "cash", null, "cat-fun", LocalDate.of(2026, 10, 14), "", "Cinema", m, TransactionSource.MANUAL))
        repo.reorderAccounts(listOf("card", "old", "cash"))
        repo.archiveAccount("old")
        repo.setGoal(rs.budget.domain.Goal("first", "First", Money.of("500", Currency.RSD), Money.zero(Currency.RSD)))
        repo.setGoal(rs.budget.domain.Goal("top", "Top", Money.of("1000", Currency.RSD), Money.zero(Currency.RSD)))
        repo.reorderGoals(listOf("top", "first"))
        val vm = HomeViewModel(repo, clock).also { models += it }
        val s = vm.state.filterNotNull().first()
        assertEquals(Money.of("750", Currency.RSD), s.summary.savingsRsd)
        assertEquals(Money.of("750", Currency.RSD), s.summary.difference)
        assertEquals(listOf("card" to Money.of("0", Currency.RSD), "cash" to Money.of("750", Currency.RSD)), s.accounts.map { it.account.id to it.balance })
        assertEquals("Old", s.accountNames["old"])
        assertEquals("Cinema", s.recent.first().transaction.merchant)
        assertEquals(m, s.currentMemberId)
        assertEquals("top", s.topGoal?.goal?.id)
        assertEquals(Money.of("750", Currency.RSD), s.topGoal?.funded)
    }
}
