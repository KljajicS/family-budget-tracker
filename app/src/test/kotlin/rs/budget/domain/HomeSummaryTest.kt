package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HomeSummaryTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val cash = Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)
    private val card = Account("card", "Card", Currency.RSD, AccountKind.SPENDING)
    private val eurSavings = Account("eur", "EUR savings", Currency.EUR, AccountKind.SAVINGS)
    private val rates = Rates(mapOf(Currency.RSD to BigDecimal.ONE, Currency.EUR to BigDecimal("117.20")))

    private fun tx(id: String, type: TransactionType, amount: String, account: Account, date: LocalDate = today, to: Account? = null, currency: Currency = Currency.RSD) =
        Transaction(id, type, Money.of(amount, currency), account.id, to?.id, null, date, "", "", "m1", TransactionSource.MANUAL)

    @Test
    fun `future transactions do not affect current balances budgets or insights`() {
        val savings = Account("savings", "Savings", Currency.RSD, AccountKind.SAVINGS)
        val txs = listOf(
            tx("posted", TransactionType.INCOME, "1000", card),
            tx("future-income", TransactionType.INCOME, "100000", card, today.plusDays(1)),
            tx("future-spend", TransactionType.EXPENSE, "500", card, today.plusDays(1)).copy(categoryId = "food"),
            tx("future-transfer", TransactionType.TRANSFER, "200", card, today.plusDays(1), to = savings),
            tx("next-month", TransactionType.INCOME, "200000", card, today.plusMonths(1)),
        )
        val month = java.time.YearMonth.from(today)
        val budget = BudgetCalculator.statuses(today, listOf(Budget("food", month, Money.of("100", Currency.RSD))), txs, rates)
        val home = HomeCalculator.summarize(today, listOf(card, savings), txs, rates)
        val insights = InsightsCalculator.summarize(today, month, txs, listOf(card, savings), budget, rates)
        assertEquals(Money.of("1000", Currency.RSD), home.safeToSpend)
        assertEquals(home.safeToSpend, home.income)
        assertEquals(Money.zero(Currency.RSD), home.savingsTotal)
        assertEquals(Money.zero(Currency.RSD), budget.single().spent)
        assertEquals(Money.zero(Currency.RSD), insights.spent)
        assertEquals(home.income, insights.months.last().income)
        assertEquals(home.savingsTotal, insights.savings.last().second)
        val tomorrow = HomeCalculator.summarize(today.plusDays(1), listOf(card, savings), txs, rates)
        assertEquals(Money.of("100300", Currency.RSD), tomorrow.safeToSpend)
        assertEquals(Money.of("200", Currency.RSD), tomorrow.savingsTotal)
    }

    @Test
    fun `income minus expenses this month, future and past months excluded`() {
        val txs = listOf(
            tx("1", TransactionType.INCOME, "245000", card),
            tx("2", TransactionType.EXPENSE, "98400", card),
            tx("3", TransactionType.EXPENSE, "5000", card, date = LocalDate.of(2026, 11, 1)),
            tx("4", TransactionType.EXPENSE, "7000", card, date = LocalDate.of(2026, 9, 30)),
        )
        val s = HomeCalculator.summarize(today, listOf(cash, card), txs, rates)
        assertEquals(Money.of("245000", Currency.RSD), s.income)
        assertEquals(Money.of("98400", Currency.RSD), s.spent)
    }

    @Test
    fun `bills and budget reserve both come off safe to spend`() {
        val txs = listOf(tx("1", TransactionType.INCOME, "100000", card))
        val s = HomeCalculator.summarize(today, listOf(card), txs, rates, committed = Money.of("10000", Currency.RSD), bills = Money.of("4300", Currency.RSD), goals = Money.of("5700", Currency.RSD), partialInputs = true)
        assertEquals(Money.of("80000", Currency.RSD), s.safeToSpend)
        assertTrue(s.partial)
    }

    @Test
    fun `transaction in another currency than its account is skipped, not thrown`() {
        val txs = listOf(
            tx("1", TransactionType.INCOME, "1000", card),
            tx("2", TransactionType.EXPENSE, "10", card, currency = Currency.EUR),
        )
        val s = HomeCalculator.summarize(today, listOf(card), txs, rates)
        assertEquals(Money.of("1000", Currency.RSD), s.balances["card"])
    }

    @Test
    fun `balances include all history and transfers move money`() {
        val txs = listOf(
            tx("1", TransactionType.INCOME, "1000", card, date = LocalDate.of(2025, 1, 1)),
            tx("2", TransactionType.TRANSFER, "400", card, to = cash),
            tx("3", TransactionType.EXPENSE, "100", cash),
        )
        val s = HomeCalculator.summarize(today, listOf(cash, card), txs, rates)
        assertEquals(Money.of("600", Currency.RSD), s.balances["card"])
        assertEquals(Money.of("300", Currency.RSD), s.balances["cash"])
        assertEquals(Money.of("900", Currency.RSD), s.safeToSpend)
        assertEquals(18, s.daysLeft)
        assertEquals(Money.of("50", Currency.RSD), s.safePerDay)
    }

    @Test
    fun `savings accounts excluded from safe to spend but summed in savings`() {
        val txs = listOf(
            tx("1", TransactionType.INCOME, "1000", card),
            tx("2", TransactionType.INCOME, "10", eurSavings, currency = Currency.EUR),
        )
        val s = HomeCalculator.summarize(today, listOf(card, eurSavings), txs, rates)
        assertEquals(Money.of("1000", Currency.RSD), s.safeToSpend)
        assertEquals(Money.of("1172", Currency.RSD), s.savingsTotal)
        assertEquals(Money.of("10", Currency.EUR), s.savingsByCurrency[Currency.EUR])
        assertFalse(s.partial)
    }

    @Test
    fun `missing rate marks summary partial and skips that account`() {
        val eurCash = Account("eurcash", "EUR cash", Currency.EUR, AccountKind.SPENDING)
        val txs = listOf(
            tx("1", TransactionType.INCOME, "1000", card),
            tx("2", TransactionType.INCOME, "10", eurCash, currency = Currency.EUR),
        )
        val s = HomeCalculator.summarize(today, listOf(card, eurCash), txs, Rates.EMPTY.plus(Currency.RSD))
        assertTrue(s.partial)
        assertEquals(Money.of("1000", Currency.RSD), s.safeToSpend)
    }

    @Test
    fun `committed money reduces safe to spend`() {
        val txs = listOf(tx("1", TransactionType.INCOME, "1000", card))
        val s = HomeCalculator.summarize(today, listOf(card), txs, rates, committed = Money.of("250", Currency.RSD))
        assertEquals(Money.of("750", Currency.RSD), s.safeToSpend)
    }
}
