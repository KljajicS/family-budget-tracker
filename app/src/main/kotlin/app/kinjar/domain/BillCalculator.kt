package app.kinjar.domain

import java.time.LocalDate
import java.time.YearMonth

data class Bill(
    val rule: RecurringRule,
    val dueDate: LocalDate,
    val period: String,
    val txId: String,
    val overdue: Boolean,
)

object BillCalculator {

    fun period(cadence: Cadence, due: LocalDate): String = when (cadence) {
        Cadence.MONTHLY -> YearMonth.from(due).toString()
        Cadence.YEARLY -> due.year.toString()
    }

    /** Due dates from the first one up to [until]. Each is computed from the first due date, so the 31st stays the 31st where the month has it. */
    fun occurrences(rule: RecurringRule, until: LocalDate): List<LocalDate> =
        generateSequence(0L) { it + 1 }
            .map { i -> if (rule.cadence == Cadence.MONTHLY) rule.firstDue.plusMonths(i) else rule.firstDue.plusYears(i) }
            .takeWhile { it <= until }
            .toList()

    /**
     * Unpaid, unskipped occurrences of active rules on active accounts, from each rule's first due date to the end of the current month.
     * Earlier months stay listed as overdue until paid or skipped.
     */
    fun upcoming(
        today: LocalDate,
        rules: List<RecurringRule>,
        activeAccountIds: Set<String>,
        transactions: List<Transaction>,
        skips: Set<Pair<String, String>>,
    ): List<Bill> {
        val monthEnd = YearMonth.from(today).atEndOfMonth()
        val paid = transactions.filter { it.date <= today }.map { it.id }.toHashSet()
        return rules
            .filter { !it.archived && it.accountId in activeAccountIds }
            .flatMap { rule ->
                occurrences(rule, monthEnd).map { due ->
                    val period = period(rule.cadence, due)
                    Bill(rule, due, period, billTxId(rule.id, period), overdue = due < today)
                }
            }
            .filter { it.txId !in paid && (it.rule.id to it.period) !in skips }
            .sortedWith(compareBy({ it.dueDate }, { it.rule.name }))
    }
}
