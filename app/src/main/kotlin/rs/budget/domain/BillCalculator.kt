package rs.budget.domain

import java.time.LocalDate
import java.time.YearMonth

/** One occurrence of a rule that is neither paid nor skipped. */
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
        val paid = transactions.map { it.id }.toHashSet()
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

    /**
     * RSD that safe to spend must hold back for [bills]: overdue ones in full, current-month ones only beyond what the
     * category's remaining budget already reserves. Second value is true when some amount had no rate and was skipped.
     */
    fun reserve(today: LocalDate, bills: List<Bill>, statuses: List<BudgetStatus>, rates: Rates): Pair<Money, Boolean> {
        var partial = false
        fun rsd(m: Money): Money = rates.convert(m) ?: Money.zero(Currency.RSD).also { partial = true }
        val month = YearMonth.from(today)
        val (thisMonth, overdue) = bills.partition { YearMonth.from(it.dueDate) == month }
        var total = overdue.fold(Money.zero(Currency.RSD)) { acc, b -> acc + rsd(b.rule.money) }
        thisMonth.groupBy { it.rule.categoryId }.forEach { (categoryId, inCategory) ->
            val due = inCategory.fold(Money.zero(Currency.RSD)) { acc, b -> acc + rsd(b.rule.money) }
            val left = statuses.firstOrNull { it.categoryId == categoryId }?.left?.takeIf { !it.isNegative }?.let(::rsd) ?: Money.zero(Currency.RSD)
            val uncovered = due - left
            if (!uncovered.isNegative) total += uncovered
        }
        return total to partial
    }
}
