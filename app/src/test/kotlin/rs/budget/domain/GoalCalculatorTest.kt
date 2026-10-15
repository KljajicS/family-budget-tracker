package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class GoalCalculatorTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val rates = Rates(mapOf(Currency.RSD to BigDecimal.ONE, Currency.EUR to BigDecimal("117.00")))
    private val rsdOnly = Rates(mapOf(Currency.RSD to BigDecimal.ONE))

    private fun goal(id: String, target: String, monthly: String, currency: Currency = Currency.RSD, archived: Boolean = false) =
        Goal(id, id, Money.of(target, currency), Money.of(monthly, currency), archived)

    private fun alloc(id: String, goal: String, amount: String, date: LocalDate = today, currency: Currency = Currency.RSD) =
        GoalAllocation(id, goal, Money.of(amount, currency), date)

    private fun rsd(s: String) = Money.of(s, Currency.RSD)

    @Test
    fun `ratio, reached and arrival month`() {
        val goals = listOf(goal("fund", "1500000", "40000"), goal("done", "1000", "100"), goal("idle", "1000", "0"))
        val allocations = listOf(alloc("a", "fund", "1200000", LocalDate.of(2026, 9, 1)), alloc("b", "done", "1000"))
        val s = GoalCalculator.statuses(today, goals, allocations).associateBy { it.goal.id }
        assertEquals(0.8, s.getValue("fund").ratio, 1e-9)
        assertFalse(s.getValue("fund").reached)
        // 300000 left at 40000 a month is 7.5 months, rounded up to 8: June 2027.
        assertEquals(YearMonth.of(2027, 6), s.getValue("fund").arrival)
        assertTrue(s.getValue("done").reached)
        assertNull(s.getValue("done").arrival)
        assertNull(s.getValue("idle").arrival)
        assertEquals(listOf("done", "fund", "idle"), GoalCalculator.statuses(today, goals, allocations).map { it.goal.id })
    }

    @Test
    fun `needs skips reached goals and converts currencies`() {
        val goals = listOf(goal("fund", "1500000", "40000"), goal("trip", "3000", "150", Currency.EUR), goal("done", "100", "50"))
        val statuses = GoalCalculator.statuses(today, goals, listOf(alloc("a", "done", "100")))
        val (needs, partial) = GoalCalculator.needs(statuses, rates)
        assertEquals(rsd("57550"), needs)
        assertFalse(partial)
        assertTrue(GoalCalculator.needs(statuses, rsdOnly).second)
    }

    @Test
    fun `hold is this month's contribution minus this month's allocations, floored, zero when reached`() {
        val goals = listOf(goal("a", "100000", "100"), goal("b", "100000", "100"), goal("c", "100000", "100"), goal("done", "100", "100"))
        val allocations = listOf(
            alloc("1", "a", "150"),
            alloc("2", "b", "40"),
            alloc("3", "c", "500", LocalDate.of(2026, 9, 30)),
            alloc("4", "done", "100"),
        )
        val statuses = GoalCalculator.statuses(today, goals, allocations)
        val (held, partial) = GoalCalculator.hold(today, statuses, allocations, rates)
        // a: capped at zero, b: 60 left, c: last month's money does not count so 100, done: nothing.
        assertEquals(rsd("160"), held)
        assertFalse(partial)
    }

    @Test
    fun `unassigned ignores archived goals and goes negative when over-assigned`() {
        val goals = listOf(goal("live", "1000", "10"), goal("gone", "1000", "10", archived = true))
        val allocations = listOf(alloc("1", "live", "700"), alloc("2", "gone", "400"))
        val statuses = GoalCalculator.statuses(today, goals, allocations)
        assertEquals(rsd("300"), GoalCalculator.unassigned(rsd("1000"), statuses, rates).first)
        assertEquals(rsd("-200"), GoalCalculator.unassigned(rsd("500"), statuses, rates).first)
    }

    @Test
    fun `allocation in another currency than its goal is skipped, never thrown on`() {
        val goals = listOf(goal("trip", "3000", "150", Currency.EUR))
        val allocations = listOf(alloc("1", "trip", "100", currency = Currency.EUR), alloc("2", "trip", "5000", currency = Currency.RSD))
        val s = GoalCalculator.statuses(today, goals, allocations).single()
        assertEquals(Money.of("100", Currency.EUR), s.allocated)
        val (held, _) = GoalCalculator.hold(today, listOf(s), allocations, rates)
        assertEquals(rsd("5850"), held)
        val (unassigned, partial) = GoalCalculator.unassigned(rsd("0"), listOf(s), rsdOnly)
        assertEquals(rsd("0"), unassigned)
        assertTrue(partial)
    }
}
