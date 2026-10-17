package rs.budget.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LoanCalculatorTest {
    private val day = LocalDate.of(2026, 10, 17)
    private fun eur(value: String) = Money.of(value, Currency.EUR)
    private val loan = Loan("l", "Car", eur("6800"), day)
    private fun tx(id: String = "t", date: LocalDate = day, amount: Money = eur("250")) =
        Transaction(id, TransactionType.EXPENSE, amount, "a", null, null, date, "", "", "m", TransactionSource.MANUAL)
    private fun payment(principal: String?, id: String = "p", tx: String = "t") =
        LoanPayment(id, "l", tx, eur("250"), principal?.let(::eur))

    @Test fun `only principal reduces debt and unknown is not zero`() {
        val paid = payment("200")
        assertEquals(eur("50"), paid.interest)
        assertEquals(eur("6600"), LoanCalculator.balance(loan, listOf(paid), emptyList(), listOf(tx()), day).beforeUnknownPayments)
        val unknown = LoanCalculator.balance(loan, listOf(payment(null)), emptyList(), listOf(tx()), day)
        assertEquals(1, unknown.incompletePayments)
        assertFalse(unknown.complete)
        assertTrue(LoanCalculator.balance(loan, listOf(payment("0")), emptyList(), listOf(tx()), day).complete)
    }

    @Test fun `actual cross currency debit is independent of principal`() {
        val result = LoanCalculator.balance(loan, listOf(payment("200")), emptyList(), listOf(tx(amount = Money.of("29350", Currency.RSD))), day)
        assertEquals(eur("6600"), result.beforeUnknownPayments)
        assertTrue(result.complete)
        assertTrue(LoanCalculator.balance(loan, listOf(payment("200")), emptyList(), listOf(tx(amount = eur("251"))), day).invalidHistory)
    }

    @Test fun `history validation nets each day and tolerates invalid replay`() {
        val small = loan.copy(opening = eur("100"))
        val adjustment = LoanAdjustment("j", "l", eur("100"), day, "Opening correction")
        assertTrue(LoanCalculator.balance(small, listOf(payment("200")), emptyList(), listOf(tx()), day).invalidHistory)
        assertTrue(LoanCalculator.balance(small, listOf(payment("200")), listOf(adjustment), listOf(tx()), day).paidOff)
        assertTrue(LoanCalculator.balance(loan, listOf(payment("200")), emptyList(), listOf(tx(date = day.minusDays(1))), day).invalidHistory)
        assertEquals(eur("6800"), LoanCalculator.balance(loan, listOf(payment("200")), emptyList(), listOf(tx(date = day.plusDays(1))), day).beforeUnknownPayments)
        assertEquals(eur("6800"), LoanCalculator.balance(loan, listOf(payment("200")), emptyList(), emptyList(), day).beforeUnknownPayments)
    }
}
