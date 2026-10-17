package rs.budget.domain

import java.math.BigDecimal
import java.time.LocalDate

object LoanCalculator {
    fun balance(loan: Loan, payments: List<LoanPayment>, adjustments: List<LoanAdjustment>, transactions: List<Transaction>, today: LocalDate): LoanBalance =
        balance(loan, payments, adjustments, transactions.associateBy { it.id }, today)

    fun balance(loan: Loan, payments: List<LoanPayment>, adjustments: List<LoanAdjustment>, txs: Map<String, Transaction>, today: LocalDate): LoanBalance {
        val changes = mutableMapOf<LocalDate, BigDecimal>()
        var incomplete = 0
        var invalid = false
        val used = mutableSetOf<String>()
        payments.filter { it.loanId == loan.id }.forEach { payment ->
            // A link whose expense vanished (deleted on another device) would otherwise read as a complete, too-high debt.
            val tx = txs[payment.transactionId] ?: run { invalid = true; return@forEach }
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
