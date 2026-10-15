package rs.budget.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Bill
import rs.budget.domain.Cadence
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.RecurringRule
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RecurringRepositoryTest {
    private val db = testDb()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")
    private val rent = RecurringRule("rent", "Rent", Money.of("40000", Currency.RSD), "cash", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 10, 20))

    private suspend fun seed(): String {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val m = repo.currentMember()!!.id
        repo.add(Transaction("t1", TransactionType.INCOME, Money.of("100000", Currency.RSD), "cash", null, null, LocalDate.of(2026, 10, 1), "", "", m, TransactionSource.MANUAL))
        return m
    }

    private fun pay(bill: Bill, member: String) = Transaction(
        bill.txId, TransactionType.EXPENSE, bill.rule.money, bill.rule.accountId, null, bill.rule.categoryId, clock.today(), "", bill.rule.name, member, TransactionSource.RECURRING,
    )

    @Test
    fun `rules are set, edited, archived and skipped through events`() = runTest {
        seed()
        repo.setRule(rent)
        repo.setRule(rent.copy(name = "Kirija"))
        assertEquals("Kirija", repo.rule("rent")!!.name)
        assertEquals(listOf("2026-10"), repo.upcomingBills.first().map { it.period })
        repo.skipBill("rent", "2026-10")
        assertEquals(emptyList<Bill>(), repo.upcomingBills.first())
        repo.archiveRule("rent")
        assertNull(repo.rules.first().firstOrNull { it.id == "rent" })
    }

    @Test
    fun `safe to spend holds the bill, stays on payment and rises on skip`() = runTest {
        val m = seed()
        val before = repo.home.first().safeToSpend
        repo.setRule(rent)
        val held = repo.home.first().safeToSpend
        assertEquals(Money.of("100000", Currency.RSD), before)
        assertEquals(Money.of("60000", Currency.RSD), held)

        val bill = repo.upcomingBills.first().single()
        repo.add(pay(bill, m))
        assertEquals(emptyList<Bill>(), repo.upcomingBills.first())
        assertEquals(Money.of("60000", Currency.RSD), repo.home.first().safeToSpend)

        repo.delete(bill.txId)
        repo.skipBill("rent", "2026-10")
        assertEquals(Money.of("100000", Currency.RSD), repo.home.first().safeToSpend)
    }

    @Test
    fun `archiving the account hides its bills`() = runTest {
        seed()
        repo.setRule(rent)
        repo.archiveAccount("cash")
        assertEquals(emptyList<Bill>(), repo.upcomingBills.first())
    }
}
