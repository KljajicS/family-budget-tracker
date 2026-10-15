package rs.budget.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** One donut slice. `categoryId` null is "Other": everything past the top four plus uncategorized spending. */
data class Slice(val categoryId: String?, val amount: Money, val share: Int)

data class MonthTotals(val month: YearMonth, val income: Money, val expenses: Money, val inProgress: Boolean)

sealed interface Tip {
    data class BudgetNearLimit(val categoryId: String, val percent: Int, val daysLeft: Int, val average: Money?) : Tip
    data class AboveAverage(val categoryId: String, val spent: Money, val average: Money) : Tip
    data class Saved(val saved: Money, val average: Money) : Tip
}

data class InsightsSummary(
    val month: YearMonth,
    val range: ClosedRange<YearMonth>,
    val spent: Money,
    val slices: List<Slice>,
    val months: List<MonthTotals>,
    val averageSaved: Money,
    val savings: List<Pair<YearMonth, Money>>,
    val savingsDelta: Money,
    val tip: Tip?,
    val partial: Boolean,
)

object InsightsCalculator {
    private const val WINDOW = 6
    private const val TOP = 4

    fun summarize(
        today: LocalDate,
        month: YearMonth,
        transactions: List<Transaction>,
        accounts: List<Account>,
        statuses: List<BudgetStatus>,
        rates: Rates,
    ): InsightsSummary {
        var partial = false
        fun rsd(m: Money): Money = rates.convert(m) ?: Money.zero(Currency.RSD).also { partial = true }
        fun sum(list: List<Transaction>) = list.fold(Money.zero(Currency.RSD)) { acc, t -> acc + rsd(t.money) }

        val current = YearMonth.from(today)
        val first = transactions.minOfOrNull { YearMonth.from(it.date) }?.coerceAtMost(current) ?: current
        val range = first..current

        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val thisMonth = expenses.filter { YearMonth.from(it.date) == month }
        val spent = sum(thisMonth)
        val slices = slices(thisMonth.groupBy { it.categoryId }.mapValues { (_, list) -> sum(list) }, spent)

        val window = (WINDOW - 1 downTo 0).map { month.minusMonths(it.toLong()) }
        val months = window.map { m ->
            val inMonth = transactions.filter { YearMonth.from(it.date) == m }
            MonthTotals(m, sum(inMonth.filter { it.type == TransactionType.INCOME }), sum(inMonth.filter { it.type == TransactionType.EXPENSE }), inProgress = m == current)
        }
        val completed = months.filter { !it.inProgress }
        val averageSaved = if (completed.isEmpty()) Money.zero(Currency.RSD) else
            Money.of(completed.fold(BigDecimal.ZERO) { acc, t -> acc + (t.income - t.expenses).amount }.divide(BigDecimal(completed.size), 2, RoundingMode.HALF_UP), Currency.RSD)

        val savingsIds = accounts.filter { it.kind == AccountKind.SAVINGS }.map { it.id }.toSet()
        val savings = window.map { m ->
            val end = m.atEndOfMonth()
            val balances = HomeCalculator.balances(accounts, transactions.filter { it.date <= end })
            m to balances.filterKeys { it in savingsIds }.values.fold(Money.zero(Currency.RSD)) { acc, b -> acc + rsd(b) }
        }
        val savingsDelta = savings.last().second - savings.first().second

        val tip = if (month == current) tip(today, month, expenses, statuses, months.last(), averageSaved, ::rsd) else null
        return InsightsSummary(month, range, spent, slices, months, averageSaved, savings, savingsDelta, tip, partial)
    }

    private fun slices(byCategory: Map<String?, Money>, total: Money): List<Slice> {
        if (total.isZero) return emptyList()
        val named = byCategory.filterKeys { it != null }.entries.sortedByDescending { it.value.amount }
        val top = named.take(TOP)
        val rest = named.drop(TOP).fold(byCategory[null] ?: Money.zero(Currency.RSD)) { acc, e -> acc + e.value }
        fun share(m: Money) = m.amount.multiply(BigDecimal(100)).divide(total.amount, 0, RoundingMode.HALF_UP).toInt()
        val slices = top.map { Slice(it.key, it.value, share(it.value)) } + (if (rest.isZero) emptyList() else listOf(Slice(null, rest, share(rest))))
        // Rounding can leave the shares at 99 or 101; the largest slice absorbs the difference so the legend adds up.
        val drift = 100 - slices.sumOf { it.share }
        return slices.mapIndexed { i, s -> if (i == 0) s.copy(share = s.share + drift) else s }
    }

    private fun tip(
        today: LocalDate,
        month: YearMonth,
        expenses: List<Transaction>,
        statuses: List<BudgetStatus>,
        thisMonth: MonthTotals,
        averageSaved: Money,
        rsd: (Money) -> Money,
    ): Tip {
        val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
        fun averageOf(categoryId: String?): Money? {
            val previous = (1..3).map { month.minusMonths(it.toLong()) }
            val sums = previous.map { m -> expenses.filter { it.categoryId == categoryId && YearMonth.from(it.date) == m }.fold(Money.zero(Currency.RSD)) { a, t -> a + rsd(t.money) } }
                .filter { !it.isZero }
            if (sums.isEmpty()) return null
            return Money.of(sums.fold(BigDecimal.ZERO) { a, m -> a + m.amount }.divide(BigDecimal(sums.size), 2, RoundingMode.HALF_UP), Currency.RSD)
        }

        statuses.filter { it.ratio >= 0.9 }.maxByOrNull { it.ratio }?.let { s ->
            return Tip.BudgetNearLimit(s.categoryId, (s.ratio * 100).toInt(), daysLeft, averageOf(s.categoryId))
        }
        expenses.filter { YearMonth.from(it.date) == month && it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, list) -> list.fold(Money.zero(Currency.RSD)) { a, t -> a + rsd(t.money) } }
            .mapNotNull { (id, spent) -> averageOf(id)?.takeIf { spent.amount > it.amount }?.let { Tip.AboveAverage(id, spent, it) } }
            .maxByOrNull { (it.spent - it.average).amount }
            ?.let { return it }
        return Tip.Saved(thisMonth.income - thisMonth.expenses, averageSaved)
    }
}
