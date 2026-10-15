package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class InsightsCalculatorTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val oct = YearMonth.of(2026, 10)
    private val rates = Rates(mapOf(Currency.RSD to BigDecimal.ONE))
    private val cash = Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)
    private val savings = Account("sav", "Savings", Currency.RSD, AccountKind.SAVINGS)
    private val accounts = listOf(cash, savings)

    private fun tx(id: String, amount: String, date: LocalDate, cat: String? = "g", type: TransactionType = TransactionType.EXPENSE, account: String = "cash", to: String? = null, currency: Currency = Currency.RSD) =
        Transaction(id, type, Money.of(amount, currency), account, to, if (type == TransactionType.EXPENSE) cat else null, date, "", "", "m1", TransactionSource.MANUAL)

    private fun rsd(s: String) = Money.of(s, Currency.RSD)

    @Test
    fun `slices are the top four plus other and shares add up to 100`() {
        val txs = listOf("a" to "300", "b" to "250", "c" to "200", "d" to "150", "e" to "50", "f" to "30").mapIndexed { i, (c, amt) -> tx("$i", amt, today, c) } +
            tx("x", "20", today, cat = null)
        val s = InsightsCalculator.summarize(today, oct, txs, accounts, emptyList(), rates)
        assertEquals(rsd("1000"), s.spent)
        assertEquals(listOf("a", "b", "c", "d", null), s.slices.map { it.categoryId })
        assertEquals(rsd("100"), s.slices.last().amount)
        assertEquals(100, s.slices.sumOf { it.share })
        assertEquals(listOf(30, 25, 20, 15, 10), s.slices.map { it.share })
    }

    @Test
    fun `income and transfers are not spending and empty input does not throw`() {
        val txs = listOf(
            tx("1", "1000", today, type = TransactionType.INCOME),
            tx("2", "500", today, type = TransactionType.TRANSFER, to = "sav"),
        )
        val s = InsightsCalculator.summarize(today, oct, txs, accounts, emptyList(), rates)
        assertEquals(rsd("0"), s.spent)
        assertEquals(emptyList<Slice>(), s.slices)
        val empty = InsightsCalculator.summarize(today, oct, emptyList(), accounts, emptyList(), rates)
        assertEquals(6, empty.months.size)
        assertEquals(6, empty.savings.size)
        assertEquals(rsd("0"), empty.savingsDelta)
        assertEquals(oct..oct, empty.range)
    }

    @Test
    fun `six month window keeps empty months and flags the month in progress`() {
        val txs = listOf(
            tx("1", "100000", LocalDate.of(2026, 5, 1), type = TransactionType.INCOME),
            tx("2", "40000", LocalDate.of(2026, 5, 2)),
            tx("3", "100000", LocalDate.of(2026, 9, 1), type = TransactionType.INCOME),
            tx("4", "70000", LocalDate.of(2026, 9, 2)),
            tx("5", "1000", today),
        )
        val s = InsightsCalculator.summarize(today, oct, txs, accounts, emptyList(), rates)
        assertEquals((5..10).map { YearMonth.of(2026, it) }, s.months.map { it.month })
        assertEquals(listOf(false, false, false, false, false, true), s.months.map { it.inProgress })
        assertEquals(rsd("0"), s.months[1].income)
        // Completed months: May +60000, Jun 0, Jul 0, Aug 0, Sep +30000, so 18000 on average.
        assertEquals(rsd("18000"), s.averageSaved)
        assertEquals(YearMonth.of(2026, 5)..oct, s.range)
    }

    @Test
    fun `savings line is cumulative, counts archived savings accounts and ignores spending accounts`() {
        val old = Account("old", "Old", Currency.RSD, AccountKind.SAVINGS, archived = true)
        val txs = listOf(
            tx("1", "500000", LocalDate.of(2026, 1, 10), type = TransactionType.INCOME),
            tx("2", "10000", LocalDate.of(2026, 6, 5), type = TransactionType.TRANSFER, to = "old"),
            tx("3", "20000", LocalDate.of(2026, 8, 5), type = TransactionType.TRANSFER, to = "sav"),
            tx("4", "5000", LocalDate.of(2026, 10, 1), type = TransactionType.TRANSFER, account = "sav", to = "cash"),
        )
        val s = InsightsCalculator.summarize(today, oct, txs, accounts + old, emptyList(), rates)
        assertEquals(listOf("0", "10000", "10000", "30000", "30000", "25000").map(::rsd), s.savings.map { it.second })
        assertEquals(rsd("25000"), s.savingsDelta)
    }

    @Test
    fun `range clamps a future dated first transaction to the current month`() {
        val s = InsightsCalculator.summarize(today, oct, listOf(tx("1", "10", LocalDate.of(2026, 12, 1))), accounts, emptyList(), rates)
        assertEquals(oct..oct, s.range)
    }

    @Test
    fun `tip priority is budget, then above average, then saved, and none for past months`() {
        val status = BudgetStatus("g", rsd("10000"), rsd("9500"), rsd("500"), 0.95, 0.45, PaceState.LOW)
        val txs = listOf(
            tx("1", "5000", LocalDate.of(2026, 8, 3), "e"),
            tx("2", "7000", LocalDate.of(2026, 9, 3), "e"),
            tx("3", "9000", today, "e"),
            tx("4", "50000", LocalDate.of(2026, 10, 1), type = TransactionType.INCOME),
        )
        val budget = InsightsCalculator.summarize(today, oct, txs, accounts, listOf(status), rates).tip as Tip.BudgetNearLimit
        assertEquals("g", budget.categoryId)
        assertEquals(95, budget.percent)
        assertEquals(18, budget.daysLeft)
        assertNull(budget.average)

        val above = InsightsCalculator.summarize(today, oct, txs, accounts, emptyList(), rates).tip as Tip.AboveAverage
        assertEquals("e", above.categoryId)
        assertEquals(rsd("9000"), above.spent)
        assertEquals(rsd("6000"), above.average)

        val saved = InsightsCalculator.summarize(today, oct, txs.take(1) + txs.last(), accounts, emptyList(), rates).tip as Tip.Saved
        assertEquals(rsd("50000"), saved.saved)

        assertNull(InsightsCalculator.summarize(today, YearMonth.of(2026, 9), txs, accounts, listOf(status), rates).tip)
    }

    @Test
    fun `amounts without a rate are skipped and flagged`() {
        val s = InsightsCalculator.summarize(today, oct, listOf(tx("1", "10", today, currency = Currency.EUR)), accounts, emptyList(), rates)
        assertTrue(s.partial)
        assertEquals(rsd("0"), s.spent)
    }
}
