package app.kinjar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SavingsPoolTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val rates = Rates(mapOf(Currency.EUR to BigDecimal("117.2")))
    private val eurAccount = Account("eur", "EUR", Currency.EUR)
    private val usdAccount = Account("usd", "USD", Currency.USD)
    private val cash = Account("cash", "Cash", Currency.RSD)

    private fun income(id: String, amount: Money, account: Account, type: TransactionType = TransactionType.INCOME) =
        Transaction(id, type, amount, account.id, null, null, today, "", "", "m", TransactionSource.MANUAL)

    @Test
    fun `a foreign balance fully funds a goal of the same amount while totals stay rounded`() {
        val home = HomeCalculator.summarize(today, listOf(eurAccount), listOf(income("t", Money.of("100.01", Currency.EUR), eurAccount)), rates)
        assertEquals(Money.of("11721.17", Currency.RSD), home.savingsRsd)
        assertEquals(Money.of("100.01", Currency.EUR), home.savingsEur)
        assertEquals(0, home.savingsPoolRsd!!.compareTo(BigDecimal("11721.172")))

        val goal = Goal("g", "Trip", Money.of("100.01", Currency.EUR))
        val progress = GoalCalculator.progress(listOf(goal, Goal("next", "Next", Money.of("1", Currency.RSD))), home, rates)
        assertTrue(progress[0].reached)
        assertEquals(goal.target, progress[0].funded)
        assertEquals(Money.zero(Currency.RSD), progress[1].funded)
        assertFalse(progress[1].waiting)

        val rounded = GoalCalculator.progress(listOf(goal), home.savingsRsd, rates)
        assertFalse("the rounded display total is the old shortfall this guards against", rounded.single().reached)
    }

    @Test
    fun `the pool never exceeds the real savings across many foreign goals`() {
        val txs = listOf(income("t", Money.of("333.33", Currency.EUR), eurAccount), income("r", Money.of("0.01", Currency.RSD), cash))
        val home = HomeCalculator.summarize(today, listOf(eurAccount, cash), txs, rates)
        val goals = (1..3).map { Goal("g$it", "G$it", Money.of("111.11", Currency.EUR)) }
        val progress = GoalCalculator.progress(goals, home, rates)
        assertTrue(progress.all { it.reached })
        val used = progress.fold(BigDecimal.ZERO) { sum, p -> sum + p.funded!!.amount.multiply(BigDecimal("117.2")) }
        assertTrue(used <= home.savingsPoolRsd!!)
    }

    @Test
    fun `zero foreign balances do not need a rate`() {
        val empty = HomeCalculator.summarize(today, listOf(cash, usdAccount), listOf(income("t", Money.of("500", Currency.RSD), cash)), rates)
        assertEquals(Money.of("500", Currency.RSD), empty.savingsRsd)
        assertEquals(Money.of("4.27", Currency.EUR), empty.savingsEur)

        val netZero = listOf(
            income("t", Money.of("500", Currency.RSD), cash),
            income("in", Money.of("10", Currency.USD), usdAccount),
            income("out", Money.of("10", Currency.USD), usdAccount, TransactionType.EXPENSE),
        )
        assertEquals(Money.of("500", Currency.RSD), HomeCalculator.summarize(today, listOf(cash, usdAccount), netZero, rates).savingsRsd)

        val zeroRsd = HomeCalculator.summarize(today, listOf(cash, eurAccount), listOf(income("e", Money.of("5", Currency.EUR), eurAccount)), Rates.EMPTY)
        assertNull(zeroRsd.savingsRsd)
        assertEquals("a zero RSD balance must not need the EUR rate for the EUR total", Money.of("5", Currency.EUR), zeroRsd.savingsEur)
    }

    @Test
    fun `a nonzero foreign balance without a rate still hides the totals`() {
        val txs = listOf(income("t", Money.of("500", Currency.RSD), cash), income("u", Money.of("1", Currency.USD), usdAccount))
        val home = HomeCalculator.summarize(today, listOf(cash, usdAccount), txs, rates)
        assertNull(home.savingsRsd)
        assertNull(home.savingsEur)
        assertNull(home.savingsPoolRsd)
        assertTrue(GoalCalculator.progress(listOf(Goal("g", "G", Money.of("1", Currency.RSD))), home, rates).single().funded == null)
    }
}
