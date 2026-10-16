package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class GoalCalculatorTest {
    private val rates = Rates(mapOf(Currency.EUR to BigDecimal("117.2"), Currency.USD to BigDecimal("3")))
    private fun rsd(s: String) = Money.of(s, Currency.RSD)
    private fun eur(s: String) = Money.of(s, Currency.EUR)
    private fun goal(id: String, target: Money) = Goal(id, id, target)

    @Test
    fun `savings fill goals one by one in priority order`() {
        val goals = listOf(goal("fund", eur("5000")), goal("home", eur("10000")), goal("trip", eur("3000")))
        val p = GoalCalculator.progress(goals, rsd("1406400"), rates)
        assertEquals(listOf(eur("5000"), eur("7000"), eur("0")), p.map { it.funded })
        assertEquals(listOf(true, false, false), p.map { it.reached })
        assertEquals(listOf(false, false, true), p.map { it.waiting })
        assertEquals(eur("3000"), p[1].left)
        assertEquals(0.7, p[1].ratio!!, 0.0001)
    }

    @Test
    fun `money is never counted twice and the residual is conserved across mixed currencies`() {
        val goals = listOf(goal("a", eur("1.01")), goal("b", rsd("333.33")), goal("c", Money.of("7.77", Currency.USD)), goal("d", rsd("1000000")))
        for (pool in listOf("0", "0.01", "118.37", "500", "523.66", "12345.67")) {
            val p = GoalCalculator.progress(goals, rsd(pool), rates)
            val used = p.fold(BigDecimal.ZERO) { acc, g -> acc + g.funded!!.amount.multiply(rates.rate(g.goal.target.currency)!!) }
            assertTrue(used <= BigDecimal(pool))
            assertTrue(BigDecimal(pool) - used < BigDecimal("1.18"))
            p.forEach { assertTrue(it.funded!!.amount <= it.goal.target.amount) }
        }
        val full = GoalCalculator.progress(goals.take(3), rsd("1000000"), rates)
        assertEquals(listOf(eur("1.01"), rsd("333.33"), Money.of("7.77", Currency.USD)), full.map { it.funded })
    }

    @Test
    fun `tiny target costs retain precision and never receive free funding`() {
        val tinyRates = Rates(mapOf(Currency.EUR to BigDecimal("0.1")))
        val goals = (1..20).map { goal(it.toString(), eur("0.01")) }
        for (pool in listOf("0", "-1")) {
            val p = GoalCalculator.progress(goals, rsd(pool), tinyRates)
            assertTrue(p.none { it.reached })
            assertTrue(p.all { it.funded == eur("0") })
        }
        val p = GoalCalculator.progress(goals, rsd("0.01"), tinyRates)
        assertEquals(10, p.count { it.reached })
        assertTrue(p.drop(10).all { it.funded == eur("0") })
    }

    @Test
    fun `negative or zero savings fund nothing and the first open goal is not waiting`() {
        val goals = listOf(goal("a", rsd("100")), goal("b", rsd("100")))
        for (pool in listOf("-500", "0")) {
            val p = GoalCalculator.progress(goals, rsd(pool), rates)
            assertEquals(listOf(rsd("0"), rsd("0")), p.map { it.funded })
            assertEquals(listOf(false, true), p.map { it.waiting })
        }
    }

    @Test
    fun `missing rate makes that goal and every later one unknown instead of skipping priority`() {
        val noUsd = Rates(mapOf(Currency.EUR to BigDecimal("117.2")))
        val goals = listOf(goal("a", rsd("100")), goal("b", Money.of("10", Currency.USD)), goal("c", rsd("100")))
        val p = GoalCalculator.progress(goals, rsd("1000"), noUsd)
        assertEquals(rsd("100"), p[0].funded)
        assertNull(p[1].funded)
        assertNull(p[2].funded)
        assertNull(p[2].ratio)
        assertTrue(GoalCalculator.progress(goals, null, rates).all { it.funded == null && !it.reached })
    }

    @Test
    fun `archived goals drop out and later goals move up`() {
        val goals = listOf(goal("a", rsd("100")).copy(archived = true), goal("b", rsd("100")))
        val p = GoalCalculator.progress(goals, rsd("100"), rates)
        assertEquals(listOf("b"), p.map { it.goal.id })
        assertTrue(p.single().reached)
        assertFalse(p.single().waiting)
    }

    @Test
    fun `full goal keeps its place and partial conversion never exceeds target`() {
        val goals = listOf(goal("a", eur("10")), goal("b", eur("10")))
        val p = GoalCalculator.progress(goals, rsd("2343.99"), rates)
        assertEquals(eur("10"), p[0].funded)
        assertTrue(p[0].reached)
        assertFalse(p[1].reached)
        assertTrue(p[1].funded!!.amount <= eur("10").amount)
    }
}
