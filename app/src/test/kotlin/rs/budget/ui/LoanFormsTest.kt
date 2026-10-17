package rs.budget.ui

import org.junit.Assert.*
import org.junit.Test
import rs.budget.domain.*
import rs.budget.ui.loans.LoanForm
import rs.budget.ui.loans.PaymentForm
import java.time.LocalDate

class LoanFormsTest {
    private val day = LocalDate.of(2026, 10, 17)
    private val loan = Loan("l", "Car", Money.of("6800", Currency.EUR), day)
    private val euro = Account("a", "Euro", Currency.EUR)
    private val rsd = Account("r", "Dinars", Currency.RSD)

    @Test fun `manual principal distinguishes empty zero invalid and greater than total`() {
        val form = PaymentForm("p", "t", day, amount = "250")
        assertNull(form.payment(loan, euro)!!.principal)
        assertEquals(Money.zero(Currency.EUR), form.copy(principal = "0").payment(loan, euro)!!.principal)
        assertNull(form.copy(principal = "oops").payment(loan, euro))
        assertNull(form.copy(principal = "251").payment(loan, euro))
        assertEquals(Money.of("50", Currency.EUR), form.copy(principal = "200").payment(loan, euro)!!.interest)
        assertNull(form.copy(amount = "0").payment(loan, euro))
    }

    @Test fun `foreign payment needs explicit loan total and same currency ignores redundant total`() {
        val form = PaymentForm("p", "t", day, amount = "29350", principal = "200")
        assertNull(form.payment(loan, rsd))
        assertEquals(Money.of("250", Currency.EUR), form.copy(total = "250").payment(loan, rsd)!!.total)
        assertEquals(Money.of("250", Currency.EUR), form.copy(amount = "250", total = "999").payment(loan, euro)!!.total)
    }

    @Test fun `loan form validates optional installment without inventing one`() {
        assertNull(LoanForm(start = day).value("l"))
        val form = LoanForm(name = "Car", opening = "6800", start = day)
        assertNull(form.value("l")!!.usualPayment)
        assertNull(form.copy(usual = "0").value("l"))
        assertNull(form.copy(usual = "invalid").value("l"))
        assertEquals(Money.of("250", Currency.EUR), form.copy(usual = "250").value("l")!!.usualPayment)
    }
}
