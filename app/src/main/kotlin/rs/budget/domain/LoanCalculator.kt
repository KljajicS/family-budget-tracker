package rs.budget.domain

import java.math.BigDecimal
import java.time.LocalDate

object LoanCalculator {
    fun balance(loan: Loan, payments: List<LoanPayment>, adjustments: List<LoanAdjustment>, transactions: List<Transaction>, today: LocalDate): LoanBalance {
        val txs = transactions.associateBy { it.id }
        val changes = mutableMapOf<LocalDate, BigDecimal>()
        var incomplete = 0
        var invalid = false
        val used = mutableSetOf<String>()
        payments.filter { it.loanId == loan.id }.forEach { payment ->
            val tx = txs[payment.transactionId] ?: return@forEach
            if (tx.date > today) return@forEach
            if (!used.add(tx.id) || tx.type != TransactionType.EXPENSE || tx.date < loan.start ||
                payment.total.currency != loan.opening.currency ||
                (tx.money.currency == payment.total.currency && tx.money != payment.total)) {
                invalid = true
                return@forEach
            }
            val principal = payment.principal
            if (principal == null) incomplete++
            else changes.merge(tx.date, principal.amount.negate(), BigDecimal::add)
        }
        adjustments.filter { it.loanId == loan.id && it.date <= today }.forEach {
            if (it.date < loan.start || it.amount.currency != loan.opening.currency) invalid = true
            else changes.merge(it.date, it.amount.amount, BigDecimal::add)
        }
        var balance = loan.opening.amount
        changes.toSortedMap().values.forEach {
            balance += it
            if (balance.signum() < 0) invalid = true
        }
        return LoanBalance(Money.of(balance, loan.opening.currency), incomplete, invalid)
    }
}
