package rs.budget.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

enum class PaceState { AHEAD, ON_TRACK, OVER_PACE, LOW, OVER }

data class BudgetStatus(
    val categoryId: String,
    val budget: Money,
    val spent: Money,
    val left: Money,
    val ratio: Double,
    val pace: Double,
    val state: PaceState,
)

object BudgetCalculator {

    /**
     * Budgets in force for [month]: per category, the latest row dated at or before the month.
     * A zero amount is a removal and ends the carry-forward. Rows are returned dated [month].
     */
    fun effective(budgets: List<Budget>, month: YearMonth): List<Budget> = budgets
        .filter { it.month <= month }
        .groupBy { it.categoryId }
        .values.map { rows -> rows.maxBy { it.month } }
        .filter { it.money.amount.signum() > 0 }
        .map { it.copy(month = month) }
        .sortedBy { it.categoryId }

    /**
     * One status per budget with a positive amount, sorted by spent ÷ budget descending.
     * Spending is converted into the budget's currency; amounts with no rate are skipped.
     */
    fun statuses(today: LocalDate, budgets: List<Budget>, transactions: List<Transaction>, rates: Rates): List<BudgetStatus> {
        val month = YearMonth.from(today)
        val pace = today.dayOfMonth.toDouble() / month.lengthOfMonth()
        val expenses = transactions.filter { it.date <= today && it.type == TransactionType.EXPENSE && it.categoryId != null && YearMonth.from(it.date) == month }

        return budgets
            .filter { it.month == month && it.money.amount.signum() > 0 }
            .map { b ->
                val budget = b.money
                val spent = expenses
                    .filter { it.categoryId == b.categoryId }
                    .fold(Money.zero(budget.currency)) { acc, t -> acc + (rates.convert(t.money, budget.currency) ?: Money.zero(budget.currency)) }
                val left = budget - spent
                val ratio = spent.amount.divide(budget.amount, 4, RoundingMode.HALF_UP).toDouble()
                val state = when {
                    left.isNegative -> PaceState.OVER
                    left.amount < budget.amount.multiply(BigDecimal("0.10")) -> PaceState.LOW
                    ratio <= pace -> PaceState.AHEAD
                    ratio <= pace + 0.10 -> PaceState.ON_TRACK
                    else -> PaceState.OVER_PACE
                }
                BudgetStatus(b.categoryId, budget, spent, left, ratio, pace, state)
            }
            .sortedByDescending { it.ratio }
    }

    /** Money the budgets still claim this month, in RSD. Overspent categories and unconvertible currencies contribute nothing. */
    fun reserve(statuses: List<BudgetStatus>, rates: Rates): Money =
        statuses.fold(Money.zero(Currency.RSD)) { acc, s -> if (s.left.isNegative) acc else acc + (rates.convert(s.left) ?: Money.zero(Currency.RSD)) }

    fun total(statuses: List<BudgetStatus>, rates: Rates): Pair<Money, Money> {
        fun sum(pick: (BudgetStatus) -> Money) = statuses.fold(Money.zero(Currency.RSD)) { a, s -> a + (rates.convert(pick(s)) ?: Money.zero(Currency.RSD)) }
        return sum { it.spent } to sum { it.budget }
    }
}
