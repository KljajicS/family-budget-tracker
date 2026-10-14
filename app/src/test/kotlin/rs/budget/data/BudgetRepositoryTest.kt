package rs.budget.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
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
}
