package rs.budget.domain

import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

data class GoalStatus(
    val goal: Goal,
    val allocated: Money,
    val ratio: Double,
    val reached: Boolean,
    val arrival: YearMonth?,
)

object GoalCalculator {
    private const val MAX_ESTIMATE_MONTHS = 1200

    fun statuses(today: LocalDate, goals: List<Goal>, allocations: List<GoalAllocation>): List<GoalStatus> =
        goals.filter { !it.archived }.map { goal ->
            val allocated = allocations
                .filter { it.date <= today && it.goalId == goal.id && it.money.currency == goal.target.currency }
                .fold(Money.zero(goal.target.currency)) { acc, a -> acc + a.money }
            val ratio = allocated.amount.divide(goal.target.amount, 4, RoundingMode.HALF_UP).toDouble()
            val reached = allocated.amount >= goal.target.amount
            val arrival = if (reached || goal.monthly.isZero) null else {
                val months = (goal.target - allocated).amount.divide(goal.monthly.amount, 0, RoundingMode.CEILING)
                if (months > java.math.BigDecimal(MAX_ESTIMATE_MONTHS)) null else YearMonth.from(today).plusMonths(months.toLong())
            }
            GoalStatus(goal, allocated, ratio, reached, arrival)
        }.sortedByDescending { it.ratio }

    fun needs(statuses: List<GoalStatus>, rates: Rates): Pair<Money, Boolean> =
        sumRsd(statuses.filter { !it.reached }.map { it.goal.monthly }, rates)

    /** RSD safe to spend must hold back: per unreached goal, this month's contribution minus what was assigned this month, floored at zero. */
    fun hold(today: LocalDate, statuses: List<GoalStatus>, allocations: List<GoalAllocation>, rates: Rates): Pair<Money, Boolean> {
        val month = YearMonth.from(today)
        val open = statuses.filter { !it.reached }.map { s ->
            val thisMonth = allocations
                .filter { it.date <= today && it.goalId == s.goal.id && it.money.currency == s.goal.monthly.currency && YearMonth.from(it.date) == month }
                .fold(Money.zero(s.goal.monthly.currency)) { acc, a -> acc + a.money }
            val left = s.goal.monthly - thisMonth
            if (left.isNegative) Money.zero(s.goal.monthly.currency) else left
        }
        return sumRsd(open, rates)
    }

    /** Savings not assigned to any active goal, in RSD. Negative means more was assigned than saved. */
    fun unassigned(savingsTotal: Money, statuses: List<GoalStatus>, rates: Rates): Pair<Money, Boolean> {
        val (assigned, partial) = sumRsd(statuses.map { it.allocated }, rates)
        return (savingsTotal - assigned) to partial
    }

    private fun sumRsd(amounts: List<Money>, rates: Rates): Pair<Money, Boolean> {
        var partial = false
        val total = amounts.fold(Money.zero(Currency.RSD)) { acc, m -> acc + (rates.convert(m) ?: Money.zero(Currency.RSD).also { partial = true }) }
        return total to partial
    }
}
