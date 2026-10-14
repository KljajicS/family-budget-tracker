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
