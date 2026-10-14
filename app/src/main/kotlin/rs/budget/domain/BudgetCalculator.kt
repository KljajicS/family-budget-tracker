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

    /** One status per budget with a positive amount, sorted by spent ÷ budget descending. Amounts not convertible to RSD are skipped. */
    fun statuses(today: LocalDate, budgets: List<Budget>, transactions: List<Transaction>, rates: Rates): List<BudgetStatus> {
        val month = YearMonth.from(today)
        val pace = today.dayOfMonth.toDouble() / month.lengthOfMonth()
        val spentByCategory = transactions
            .filter { it.type == TransactionType.EXPENSE && it.categoryId != null && YearMonth.from(it.date) == month }
            .groupBy { it.categoryId!! }
            .mapValues { (_, txs) -> txs.fold(Money.zero(Currency.RSD)) { acc, t -> acc + (rates.convert(t.money) ?: Money.zero(Currency.RSD)) } }

        return budgets
            .filter { it.month == month && it.money.amount.signum() > 0 }
            .map { b ->
                val budget = rates.convert(b.money) ?: Money.zero(Currency.RSD)
                val spent = spentByCategory[b.categoryId] ?: Money.zero(Currency.RSD)
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

    /** Money the budgets still claim this month. Overspent categories contribute nothing. */
    fun reserve(statuses: List<BudgetStatus>): Money =
        statuses.fold(Money.zero(Currency.RSD)) { acc, s -> if (s.left.isNegative) acc else acc + s.left }

    /** Total spent and total budgeted across all statuses. */
    fun total(statuses: List<BudgetStatus>): Pair<Money, Money> =
        statuses.fold(Money.zero(Currency.RSD)) { a, s -> a + s.spent } to statuses.fold(Money.zero(Currency.RSD)) { a, s -> a + s.budget }
}
