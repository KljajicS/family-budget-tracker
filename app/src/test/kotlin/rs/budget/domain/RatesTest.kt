package rs.budget.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class RatesTest {
    @Test fun `cross conversion rounds only final amount`() {
        val rates = Rates(mapOf(Currency.EUR to BigDecimal("1.49"), Currency.USD to BigDecimal("2")))
        assertEquals(Money.of("0.01", Currency.USD), rates.convert(Money.of("0.02", Currency.EUR), Currency.USD))
        assertEquals(Money.of("2", Currency.RSD), rates.convert(Money.of("1", Currency.USD)))
        assertEquals(Money.of("1", Currency.USD), rates.convert(Money.of("1", Currency.USD), Currency.USD))
        assertNull(Rates.EMPTY.convert(Money.of("1", Currency.EUR)))
    }

    @Test fun `rate inputs are bounded and RSD cannot change`() {
        listOf("0", "-1", "NaN", "1e2147483647", "1000001", "1.123456789", "9".repeat(1000)).forEach { assertNull(parseRate(it)) }
        assertEquals(BigDecimal("117.12345678"), parseRate("117.12345678"))
        assertTrue(runCatching { Rates(mapOf(Currency.EUR to BigDecimal.ZERO)) }.isFailure)
        assertTrue(runCatching { Rates(mapOf(Currency.RSD to BigDecimal("2"))) }.isFailure)
        val values = mutableMapOf(Currency.EUR to BigDecimal("117"))
        val rates = Rates(values)
        values[Currency.EUR] = BigDecimal.ONE
        assertEquals(BigDecimal("117"), rates.rate(Currency.EUR))
    }

    @Test fun `missing expense conversion marks budget incomplete`() {
        val day = LocalDate.of(2026, 10, 15)
        val budget = Budget("food", YearMonth.from(day), Money.of("1000", Currency.RSD))
        val tx = Transaction("t", TransactionType.EXPENSE, Money.of("10", Currency.EUR), "cash", null, "food", day, "", "", "m", TransactionSource.MANUAL)
        val statuses = BudgetCalculator.statuses(day, listOf(budget), listOf(tx), Rates.EMPTY)
        assertEquals(setOf(Currency.EUR), statuses.single().missingCurrencies)
        assertEquals(setOf(Currency.EUR), BudgetCalculator.missingCurrencies(statuses, Rates.EMPTY))
        val priced = BudgetCalculator.statuses(day, listOf(budget), listOf(tx), Rates.EMPTY.plus(Currency.EUR, BigDecimal("117")))
        assertEquals(Money.of("1170", Currency.RSD), priced.single().spent)
        assertTrue(BudgetCalculator.missingCurrencies(priced, Rates.EMPTY.plus(Currency.EUR)).isEmpty())
    }

    @Test fun `missing budget currency marks totals partial even without expenses`() {
        val day = LocalDate.of(2026, 10, 15)
        val budget = Budget("food", YearMonth.from(day), Money.of("100", Currency.EUR))
        val tx = Transaction("t", TransactionType.EXPENSE, Money.of("117", Currency.RSD), "cash", null, "food", day, "", "", "m", TransactionSource.MANUAL)
        for (transactions in listOf(emptyList(), listOf(tx))) {
            val statuses = BudgetCalculator.statuses(day, listOf(budget), transactions, Rates.EMPTY)
            assertEquals(setOf(Currency.EUR), BudgetCalculator.missingCurrencies(statuses, Rates.EMPTY))
        }
        val rates = Rates.EMPTY.plus(Currency.EUR, BigDecimal("117"))
        val priced = BudgetCalculator.statuses(day, listOf(budget), listOf(tx), rates)
        assertEquals(Money.of("1", Currency.EUR), priced.single().spent)
        assertTrue(BudgetCalculator.missingCurrencies(priced, rates).isEmpty())
    }
}
