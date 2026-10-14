package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class BudgetCalculatorTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val month = YearMonth.of(2026, 10)
    private val rates = Rates(mapOf(Currency.RSD to BigDecimal.ONE))

    private fun tx(id: String, amount: String, cat: String?, date: LocalDate = today, type: TransactionType = TransactionType.EXPENSE) =
        Transaction(id, type, Money.of(amount, Currency.RSD), "cash", null, cat, date, "", "", "m1", TransactionSource.MANUAL)

    private fun budget(cat: String, amount: String) = Budget(cat, month, Money.of(amount, Currency.RSD))

    @Test
    fun `spent counts only this month's expenses in the category`() {
        val s = BudgetCalculator.statuses(
            today, listOf(budget("g", "60000")),
            listOf(
                tx("1", "38200", "g"),
                tx("2", "5000", "g", date = LocalDate.of(2026, 9, 30)),
                tx("3", "1000", "g", date = LocalDate.of(2026, 11, 1)),
                tx("4", "7000", "other"),
                tx("5", "9000", "g", type = TransactionType.INCOME),
            ),
            rates,
        ).single()
        assertEquals(Money.of("38200", Currency.RSD), s.spent)
        assertEquals(Money.of("21800", Currency.RSD), s.left)
    }

    @Test
    fun `pace states`() {
        val statuses = BudgetCalculator.statuses(
            today,
            listOf(budget("ahead", "1000"), budget("track", "1000"), budget("overpace", "1000"), budget("low", "1000"), budget("over", "1000")),
            listOf(tx("1", "400", "ahead"), tx("2", "520", "track"), tx("3", "700", "overpace"), tx("4", "950", "low"), tx("5", "1200", "over")),
            rates,
        ).associateBy { it.categoryId }
        assertEquals(PaceState.AHEAD, statuses["ahead"]!!.state)
        assertEquals(PaceState.ON_TRACK, statuses["track"]!!.state)
        assertEquals(PaceState.OVER_PACE, statuses["overpace"]!!.state)
        assertEquals(PaceState.LOW, statuses["low"]!!.state)
        assertEquals(PaceState.OVER, statuses["over"]!!.state)
        assertTrue(statuses["over"]!!.left.isNegative)
    }

    @Test
    fun `zero budget is skipped and reserve ignores overspent categories`() {
        val statuses = BudgetCalculator.statuses(
            today, listOf(budget("zero", "0"), budget("a", "1000"), budget("b", "1000")),
            listOf(tx("1", "300", "a"), tx("2", "1500", "b")),
            rates,
        )
        assertEquals(listOf("b", "a"), statuses.map { it.categoryId })
        assertEquals(Money.of("700", Currency.RSD), BudgetCalculator.reserve(statuses))
        val (spent, total) = BudgetCalculator.total(statuses)
        assertEquals(Money.of("1800", Currency.RSD), spent)
        assertEquals(Money.of("2000", Currency.RSD), total)
    }

    @Test
    fun `statuses sort by ratio descending`() {
        val statuses = BudgetCalculator.statuses(
            today, listOf(budget("a", "1000"), budget("b", "1000"), budget("c", "1000")),
            listOf(tx("1", "100", "a"), tx("2", "900", "b"), tx("3", "500", "c")),
            rates,
        )
        assertEquals(listOf("b", "c", "a"), statuses.map { it.categoryId })
    }
}
