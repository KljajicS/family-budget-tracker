package rs.budget.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Budget
import rs.budget.domain.BudgetStatus
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class BudgetRepositoryTest {
    private val db = testDb()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val store = EventStore(db, "dev-1", clock, Projector(db))
    private val repo = BudgetRepository(db, store, clock)

    @Test
    fun `setUp creates member, starter categories and accounts`() = runTest {
        repo.setUp("Siniša", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        assertNotNull(repo.currentMember())
        assertEquals(StarterCategories.all.size, repo.categories.first().size)
        assertEquals("Cash", repo.accounts.first().single().name)
    }

    @Test
    fun `add shows up in recent and home`() = runTest {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val member = repo.currentMember()!!
        repo.add(Transaction("t1", TransactionType.INCOME, Money.of("1000", Currency.RSD), "cash", null, null, LocalDate.of(2026, 10, 14), "", "", member.id, TransactionSource.MANUAL))
        repo.add(Transaction("t2", TransactionType.EXPENSE, Money.of("300", Currency.RSD), "cash", null, "cat-groceries", LocalDate.of(2026, 10, 14), "", "Maxi", member.id, TransactionSource.MANUAL))
        val recent = repo.recent(10).first()
        assertEquals("Maxi", recent.first().transaction.merchant)
        assertEquals("Groceries", recent.first().categoryName)
        assertEquals("S", recent.first().paidByName)
        assertEquals(Money.of("700", Currency.RSD), repo.home.first().safeToSpend)
    }

    @Test
    fun `budgets copy forward until the month gets its own`() = runTest {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val sep = YearMonth.of(2026, 9)
        val oct = YearMonth.of(2026, 10)
        repo.setBudget(Budget("cat-groceries", sep, Money.of("50000", Currency.RSD)))
        assertEquals(listOf(Budget("cat-groceries", oct, Money.of("50000", Currency.RSD))), repo.effectiveBudgets(oct).first())
        repo.setBudget(Budget("cat-groceries", oct, Money.of("60000", Currency.RSD)))
        assertEquals(Money.of("60000", Currency.RSD), repo.effectiveBudgets(oct).first().single().money)
        assertEquals(Money.of("50000", Currency.RSD), repo.effectiveBudgets(sep).first().single().money)
    }

    @Test
    fun `removing a carried-forward budget ends it from that month on, earlier months keep it`() = runTest {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val sep = YearMonth.of(2026, 9)
        val oct = YearMonth.of(2026, 10)
        val nov = YearMonth.of(2026, 11)
        repo.setBudget(Budget("cat-groceries", sep, Money.of("50000", Currency.RSD)))
        repo.setBudget(Budget("cat-fun", sep, Money.of("5000", Currency.EUR)))
        repo.removeBudget("cat-groceries", oct)
        assertEquals(listOf(Budget("cat-fun", oct, Money.of("5000", Currency.EUR))), repo.effectiveBudgets(oct).first())
        assertEquals(listOf("cat-fun"), repo.effectiveBudgets(nov).first().map { it.categoryId })
        assertEquals(2, repo.effectiveBudgets(sep).first().size)
    }

    @Test
    fun `removing the last budget of a month does not bring the previous month back`() = runTest {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        repo.setBudget(Budget("cat-groceries", YearMonth.of(2026, 9), Money.of("50000", Currency.RSD)))
        repo.removeBudget("cat-groceries", YearMonth.of(2026, 10))
        assertEquals(emptyList<Budget>(), repo.effectiveBudgets(YearMonth.of(2026, 10)).first())
        assertEquals(emptyList<BudgetStatus>(), repo.budgetStatuses.first())
    }

    @Test
    fun `no budgets anywhere gives empty statuses and no reserve`() = runTest {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        assertEquals(emptyList<BudgetStatus>(), repo.budgetStatuses.first())
    }

    @Test
    fun `budget reserve lowers safe to spend`() = runTest {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val m = repo.currentMember()!!.id
        repo.add(Transaction("t1", TransactionType.INCOME, Money.of("100000", Currency.RSD), "cash", null, null, LocalDate.of(2026, 10, 1), "", "", m, TransactionSource.MANUAL))
        repo.add(Transaction("t2", TransactionType.EXPENSE, Money.of("4000", Currency.RSD), "cash", null, "cat-groceries", LocalDate.of(2026, 10, 14), "", "", m, TransactionSource.MANUAL))
        val before = repo.home.first().safeToSpend
        repo.setBudget(Budget("cat-groceries", YearMonth.of(2026, 10), Money.of("10000", Currency.RSD)))
        repo.setBudget(Budget("cat-fun", YearMonth.of(2026, 10), Money.of("5000", Currency.RSD)))
        val after = repo.home.first().safeToSpend
        assertEquals(Money.of("96000", Currency.RSD), before)
        assertEquals(Money.of("85000", Currency.RSD), after)
        val s = repo.budgetStatuses.first()
        assertEquals(listOf("cat-groceries", "cat-fun"), s.map { it.categoryId })
    }
}
