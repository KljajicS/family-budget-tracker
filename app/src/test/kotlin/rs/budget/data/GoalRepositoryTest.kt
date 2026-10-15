package rs.budget.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

class GoalRepositoryTest {
    private val db = testDb()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")
    private val fund = Goal("fund", "Emergency fund", Money.of("100000", Currency.RSD), Money.of("10000", Currency.RSD))

    private suspend fun seed() {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val m = repo.currentMember()!!.id
        repo.add(Transaction("t1", TransactionType.INCOME, Money.of("100000", Currency.RSD), "cash", null, null, LocalDate.of(2026, 10, 1), "", "", m, TransactionSource.MANUAL))
    }

    @Test
    fun `goals are set, edited, funded and archived through events`() = runTest {
        seed()
        repo.setGoal(fund)
        repo.setGoal(fund.copy(name = "Rezerva"))
        assertEquals("Rezerva", repo.goal("fund")!!.name)
        repo.allocate(GoalAllocation("a1", "fund", Money.of("25000", Currency.RSD), LocalDate.of(2026, 10, 5)))
        repo.allocate(GoalAllocation("a2", "fund", Money.of("5000", Currency.RSD), LocalDate.of(2026, 10, 6)))
        assertEquals(Money.of("30000", Currency.RSD), repo.goalStatuses.first().single().allocated)
        repo.deleteAllocation("a2")
        assertEquals(Money.of("25000", Currency.RSD), repo.goalStatuses.first().single().allocated)
        repo.archiveGoal("fund")
        assertEquals(0, repo.goals.first().size)
        repo.setGoal(fund.copy(name = "Back"))
        assertNull(repo.goals.first().firstOrNull { it.id == "fund" })
    }

    @Test
    fun `safe to spend holds the contribution until this month's money is assigned`() = runTest {
        seed()
        assertEquals(Money.of("100000", Currency.RSD), repo.home.first().safeToSpend)
        repo.setGoal(fund)
        assertEquals(Money.of("90000", Currency.RSD), repo.home.first().safeToSpend)
        repo.allocate(GoalAllocation("a1", "fund", Money.of("4000", Currency.RSD), LocalDate.of(2026, 10, 5)))
        assertEquals(Money.of("94000", Currency.RSD), repo.home.first().safeToSpend)
        repo.allocate(GoalAllocation("a2", "fund", Money.of("9000", Currency.RSD), LocalDate.of(2026, 10, 6)))
        assertEquals(Money.of("100000", Currency.RSD), repo.home.first().safeToSpend)
    }

    @Test
    fun `a goal without a rate flags home as partial`() = runTest {
        seed()
        repo.setGoal(Goal("trip", "Trip", Money.of("3000", Currency.EUR), Money.of("150", Currency.EUR)))
        assertTrue(repo.home.first().partial)
    }
}
